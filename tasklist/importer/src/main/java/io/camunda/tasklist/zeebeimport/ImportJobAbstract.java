/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ElasticsearchUtil.ZEEBE_INDEX_DELIMITER;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class ImportJobAbstract implements ImportJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportJob.class);

  protected ImportBatch importBatch;

  protected ImportPositionEntity previousPosition;

  protected ImportPositionEntity lastProcessedPosition;

  protected OffsetDateTime creationTime;

  @Autowired protected ImportPositionHolder importPositionHolder;

  @Autowired protected RecordsReaderHolder recordsReaderHolder;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private ImportBatchProcessorFactory importBatchProcessorFactory;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Override
  public Boolean call() {
    processPossibleIndexChange();

    // separate importbatch in sub-batches per index
    final List<ImportBatch> subBatches = createSubBatchesPerIndexName();

    for (final ImportBatch subBatch : subBatches) {
      final boolean success = processOneIndexBatch(subBatch);
      if (!success) {
        notifyImportListenersAsFailed(importBatch);
        return false;
      } // else continue
    }
    importPositionHolder.recordLatestLoadedPosition(getLastProcessedPosition());
    for (final ImportBatch subBatch : subBatches) {
      notifyImportListenersAsFinished(subBatch);
    }
    return true;
  }

  private String extractZeebeVersionFromIndexName(final String indexName) {
    final String[] split = indexName.split(ZEEBE_INDEX_DELIMITER);
    final String zeebeVersion;
    if (split.length >= 3) {
      zeebeVersion = split[2].replace("-snapshot", "");
    } else {
      // last version before introducing versions in index names was 0.22.0
      zeebeVersion = "0.22.0";
    }
    return zeebeVersion;
  }

  private boolean processOneIndexBatch(final ImportBatch subBatch) {
    try {
      final String version = extractZeebeVersionFromIndexName(subBatch.getLastRecordIndexName());

      final var batchVersion = SemanticVersion.fromVersion(version);

      if (batchVersion.getMajor() == 8 && batchVersion.getMinor() == 8) {
        recordsReaderHolder.addPartitionCompletedImporting(subBatch.getPartitionId());
        return true;
      }

      final ImportBatchProcessor importBatchProcessor =
          importBatchProcessorFactory.getImportBatchProcessor(version);
      importBatchProcessor.performImport(subBatch);

      return true;
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      return false;
    }
  }

  @Override
  public void recordLatestScheduledPosition() {
    importPositionHolder.recordLatestScheduledPosition(
        importBatch.getAliasName(), importBatch.getPartitionId(), getLastProcessedPosition());
  }

  @Override
  public ImportPositionEntity getLastProcessedPosition() {
    if (lastProcessedPosition == null) {
      final long lastRecordPosition = importBatch.getLastProcessedPosition(objectMapper);
      final long lastSequence = importBatch.getLastProcessedSequence(objectMapper);
      if (lastRecordPosition != 0 || lastSequence != 0) {
        lastProcessedPosition =
            ImportPositionEntity.createFrom(
                lastSequence,
                previousPosition,
                lastRecordPosition,
                importBatch.getLastRecordIndexName(),
                previousPosition.getCompleted());
      } else {
        lastProcessedPosition = previousPosition;
      }
    }
    return lastProcessedPosition;
  }

  @Override
  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null
        && previousPosition != null
        && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
    }
  }

  @Override
  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  @Override
  public void processPossibleIndexChange() {
    // if there was index change, comparing with previous batch, or there are more than one indices
    // in current batch, refresh Zeebe indices

    if (indexChange() || importBatch.hasMoreThanOneUniqueHitId()) {
      refreshZeebeIndices();
      // reread batch
      final RecordsReader recordsReader =
          recordsReaderHolder.getRecordsReader(
              importBatch.getPartitionId(), importBatch.getImportValueType());
      if (recordsReader == null) {
        LOGGER.warn(
            "Unable to find records reader for partitionId {} and ImportValueType {}",
            importBatch.getPartitionId(),
            importBatch.getImportValueType());
        return;
      }
      try {
        final boolean useOnlyPosition = tasklistProperties.getImporter().isUseOnlyPosition();
        final ImportBatch newImportBatch;
        if (!useOnlyPosition && previousPosition.getSequence() > 0) {
          newImportBatch =
              recordsReader.readNextBatchBySequence(
                  previousPosition.getSequence(),
                  importBatch.getLastProcessedSequence(objectMapper));

          final Long lastSequenceFromInitialBatch =
              importBatch.getLastProcessedSequence(objectMapper);
          final Long lastSequenceFromNewImportBatch =
              newImportBatch.getLastProcessedSequence(objectMapper);

          if (newImportBatch == null
              || newImportBatch.getHits() == null
              || lastSequenceFromInitialBatch > lastSequenceFromNewImportBatch) {
            final String message =
                String.format(
                    "Warning! Import batch became smaller after reread. Should not happen. Will be retried. Expected last sequence %d, actual last sequence %d.",
                    lastSequenceFromInitialBatch, lastSequenceFromNewImportBatch);
            throw new TasklistRuntimeException(message);
          }
        } else {
          newImportBatch =
              recordsReader.readNextBatchByPositionAndPartition(
                  previousPosition.getPosition(),
                  importBatch.getLastProcessedPosition(objectMapper));
          if (newImportBatch == null
              || newImportBatch.getHits() == null
              || newImportBatch.getHits().size() < importBatch.getHits().size()) {
            throw new TasklistRuntimeException(
                "Warning! Import batch became smaller after reread. Should not happen. Will be retried.");
          }
        }
        importBatch = newImportBatch;
      } catch (final NoSuchIndexException ex) {
        LOGGER.warn("Indices are not found" + importBatch.toString());
      }
    }
  }

  protected void notifyImportListenersAsFinished(final ImportBatch importBatch) {
    if (importListeners != null) {
      for (final ImportListener importListener : importListeners) {
        importListener.finished(importBatch);
      }
    }
  }

  protected void notifyImportListenersAsFailed(final ImportBatch importBatch) {
    if (importListeners != null) {
      for (final ImportListener importListener : importListeners) {
        importListener.failed(importBatch);
      }
    }
  }
}
