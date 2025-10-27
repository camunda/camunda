/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static org.apache.commons.io.output.NullOutputStream.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ZeebeStore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Import job for one batch of Zeebe data. */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportJob implements Callable<Boolean> {

  public static final String ZEEBE_INDEX_DELIMITER = "_";

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportJob.class);
  private final ImportPositionEntity previousPosition;
  private final OffsetDateTime creationTime;
  private ImportBatch importBatch;
  private ImportPositionEntity lastProcessedPosition;
  @Autowired private ImportBatchProcessorFactory importBatchProcessorFactory;
  @Autowired private ImportPositionHolder importPositionHolder;
  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Autowired private ZeebeStore zeebeStore;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private OperateProperties operateProperties;

  public ImportJob(final ImportBatch importBatch, final ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
    creationTime = OffsetDateTime.now();
  }

  @Override
  public Boolean call() {
    processPossibleIndexChange();

    // separate importBatch in sub-batches per index
    final List<ImportBatch> subBatches = createSizeLimitedSubBatchesByIndexName();

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

  @SuppressWarnings("checkstyle:NestedIfDepth")
  private void processPossibleIndexChange() {
    // if there was index change, comparing with previous batch, or there are more than one index in
    // current batch, refresh Zeebe indices
    final List<HitEntity> hits = importBatch.getHits();
    final boolean useOnlyPosition = operateProperties.getImporter().isUseOnlyPosition();
    if (indexChange()
        || hits.stream().map(HitEntity::getIndex).collect(Collectors.toSet()).size() > 1) {
      refreshZeebeIndices();
      // reread batch
      final RecordsReader recordsReader =
          recordsReaderHolder.getRecordsReader(
              importBatch.getPartitionId(), importBatch.getImportValueType());
      if (recordsReader != null) {
        try {
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

            if (newImportBatch.getHits() == null
                || lastSequenceFromInitialBatch > lastSequenceFromNewImportBatch) {
              final String message =
                  String.format(
                      "Warning! Import batch became smaller after reread. Should not happen. Will be retried. Expected last sequence %d, actual last sequence %d.",
                      lastSequenceFromInitialBatch, lastSequenceFromNewImportBatch);
              throw new OperateRuntimeException(message);
            }

          } else {
            newImportBatch =
                recordsReader.readNextBatchByPositionAndPartition(
                    previousPosition.getPosition(),
                    importBatch.getLastProcessedPosition(objectMapper));

            if (newImportBatch == null
                || newImportBatch.getHits() == null
                || newImportBatch.getHits().size() < importBatch.getHits().size()) {
              throw new OperateRuntimeException(
                  "Warning! Import batch became smaller after reread. Should not happen. Will be retried.");
            }
          }
          importBatch = newImportBatch;
        } catch (final NoSuchIndexException ex) {
          LOGGER.warn("Indices are not found" + importBatch.toString());
        }
      } else {
        LOGGER.warn(
            "Unable to find records reader for partitionId {} and ImportValueType {}",
            importBatch.getPartitionId(),
            importBatch.getImportValueType());
      }
    }
  }

  private boolean processOneIndexBatch(final ImportBatch subBatch) {
    try {
      final String version = extractZeebeVersionFromIndexName(subBatch.getLastRecordIndexName());
      final ImportBatchProcessor importBatchProcessor =
          importBatchProcessorFactory.getImportBatchProcessor(version);

      importBatchProcessor.performImport(subBatch);
      return true;
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      return false;
    }
  }

  private List<ImportBatch> createSizeLimitedSubBatchesByIndexName() {
    final List<ImportBatch> subBatches = new ArrayList<>();
    if (importBatch.getHits().size() <= 1) {
      subBatches.add(importBatch);
      return subBatches;
    } else {
      final long maxBatchSizeBytes = operateProperties.getImporterMaxBatchSizeBytes();
      String previousIndexName = null;
      List<HitEntity> subBatchHits = new ArrayList<>();
      long subBatchSize = 0;
      for (final HitEntity hit : importBatch.getHits()) {
        final String indexName = hit.getIndex();
        final long hitSize = EntitySizeEstimator.estimateEntitySize(hit);
        final boolean indexChanged =
            previousIndexName != null && !indexName.equals(previousIndexName);
        final boolean sizeExceeded = subBatchSize + hitSize > maxBatchSizeBytes;
        // If index name changed or max size exceeded, finalize current batch and start a new one
        if ((indexChanged && !subBatchHits.isEmpty()) || sizeExceeded) {
          subBatches.add(
              new ImportBatch(
                  importBatch.getPartitionId(),
                  importBatch.getImportValueType(),
                  subBatchHits,
                  previousIndexName));
          subBatchHits = new ArrayList<>();
          subBatchSize = 0;
        }
        // Add current hit to the batch and update size
        subBatchHits.add(hit);
        subBatchSize += hitSize;
        previousIndexName = indexName;
      }
      // Add any remaining hits as the final batch
      if (!subBatchHits.isEmpty()) {
        subBatches.add(
            new ImportBatch(
                importBatch.getPartitionId(),
                importBatch.getImportValueType(),
                subBatchHits,
                previousIndexName));
      }
      return subBatches;
    }
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

  public void refreshZeebeIndices() {
    final String indexPattern =
        importBatch
            .getImportValueType()
            .getIndicesPattern(operateProperties.getZeebeElasticsearch().getPrefix());
    zeebeStore.refreshIndex(indexPattern);
  }

  public void recordLatestScheduledPosition() {
    importPositionHolder.recordLatestScheduledPosition(
        importBatch.getAliasName(), importBatch.getPartitionId(), getLastProcessedPosition());
  }

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
                importBatch.getLastRecordIndexName());
      } else {
        lastProcessedPosition = previousPosition;
      }
    }
    return lastProcessedPosition;
  }

  public ImportBatch getImportBatch() {
    return importBatch;
  }

  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null
        && previousPosition != null
        && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
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

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  private static final class EntitySizeEstimator {
    private static final ThreadLocal<Kryo> THREAD_LOCAL_KRYO =
        ThreadLocal.withInitial(
            () -> {
              final Kryo kryo = new Kryo();
              kryo.setRegistrationRequired(false);
              kryo.setReferences(false);
              return kryo;
            });

    public static long estimateEntitySize(final HitEntity entity) {
      final Kryo kryo = THREAD_LOCAL_KRYO.get();
      try (final CountingOutputStream countingStream = new CountingOutputStream(INSTANCE);
          final Output output = new Output(countingStream, 8192)) {
        kryo.writeObject(output, entity);
        kryo.reset();
        return countingStream.getByteCount();
      } catch (final Exception e) {
        throw new RuntimeException("Failed to estimate object size", e);
      }
    }
  }
}
