/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ElasticsearchUtil.ZEEBE_INDEX_DELIMITER;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportJob.class);

  private ImportBatch importBatch;

  private ImportPositionEntity previousPosition;

  private ImportPositionEntity lastProcessedPosition;

  private OffsetDateTime creationTime;

  @Autowired private ImportBatchProcessorFactory importBatchProcessorFactory;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private ImportPositionHolder importPositionHolder;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  public ImportJob(ImportBatch importBatch, ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
    this.creationTime = OffsetDateTime.now();
  }

  @Override
  public Boolean call() {
    processPossibleIndexChange();

    // separate importbatch in sub-batches per index
    final List<ImportBatch> subBatches = createSubBatchesPerIndexName();

    for (ImportBatch subBatch : subBatches) {
      final boolean success = processOneIndexBatch(subBatch);
      if (!success) {
        notifyImportListenersAsFailed(importBatch);
        return false;
      } // else continue
    }
    importPositionHolder.recordLatestLoadedPosition(getLastProcessedPosition());
    for (ImportBatch subBatch : subBatches) {
      notifyImportListenersAsFinished(subBatch);
    }
    return true;
  }

  private void processPossibleIndexChange() {
    // if there was index change, comparing with previous batch, or there are more than one indices
    // in current batch, refresh Zeebe indices
    final List<SearchHit> hits = importBatch.getHits();
    if (indexChange()
        || hits.stream().map(SearchHit::getIndex).collect(Collectors.toSet()).size() > 1) {
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
        final ImportBatch newImportBatch;
        if (previousPosition.getSequence() > 0) {
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
      } catch (NoSuchIndexException ex) {
        LOGGER.warn("Indices are not found" + importBatch.toString());
      }
    }
  }

  private boolean processOneIndexBatch(ImportBatch subBatch) {
    try {
      final String version = extractZeebeVersionFromIndexName(subBatch.getLastRecordIndexName());
      final ImportBatchProcessor importBatchProcessor =
          importBatchProcessorFactory.getImportBatchProcessor(version);
      importBatchProcessor.performImport(subBatch);
      return true;
    } catch (Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      return false;
    }
  }

  private List<ImportBatch> createSubBatchesPerIndexName() {
    final List<ImportBatch> subBatches = new ArrayList<>();
    if (importBatch.getHits().size() <= 1) {
      subBatches.add(importBatch);
      return subBatches;
    } else {
      String previousIndexName = null;
      List<SearchHit> subBatchHits = new ArrayList<>();
      for (SearchHit hit : importBatch.getHits()) {
        final String indexName = hit.getIndex();
        if (previousIndexName != null && !indexName.equals(previousIndexName)) {
          // start new sub-batch
          subBatches.add(
              new ImportBatch(
                  importBatch.getPartitionId(),
                  importBatch.getImportValueType(),
                  subBatchHits,
                  previousIndexName));
          subBatchHits = new ArrayList<>();
        }
        subBatchHits.add(hit);
        previousIndexName = indexName;
      }
      subBatches.add(
          new ImportBatch(
              importBatch.getPartitionId(),
              importBatch.getImportValueType(),
              subBatchHits,
              previousIndexName));
      return subBatches;
    }
  }

  private String extractZeebeVersionFromIndexName(String indexName) {
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
            .getIndicesPattern(tasklistProperties.getZeebeElasticsearch().getPrefix());
    ElasticsearchUtil.refreshIndicesFor(zeebeEsClient, indexPattern);
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

  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null
        && previousPosition != null
        && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
    }
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  protected void notifyImportListenersAsFinished(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.finished(importBatch);
      }
    }
  }

  protected void notifyImportListenersAsFailed(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.failed(importBatch);
      }
    }
  }
}
