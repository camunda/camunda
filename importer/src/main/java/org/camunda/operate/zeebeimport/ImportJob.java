/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Import job for one batch of Zeebe data.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportJob implements Callable<Boolean> {

  private static final Logger logger = LoggerFactory.getLogger(ImportJob.class);

  private ImportBatch importBatch;

  private ImportPositionEntity previousPosition;

  private ImportPositionEntity lastProcessedPosition;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  public ImportJob(ImportBatch importBatch, ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
  }

  @Override
  public Boolean call() {
    try {
      //at midnight index is changed and some events may be lost, see OPE-733
      if (indexChange()) {
        refreshPreviousIndex();
        RecordsReader recordsReader = recordsReaderHolder.getRecordsReader(importBatch.getPartitionId(), importBatch.getImportValueType());
        if (recordsReader != null) {
          //reread same batch (mark index as done - may be just empty index name in batch)
          importBatch = recordsReader.readNextBatch(previousPosition.getPosition(), importBatch.getLastProcessedPosition());
          markIndexAsDone();
        } else {
          logger.warn("Unable to find records reader for partitionId {} and ImportValueType {}", importBatch.getPartitionId(), importBatch.getImportValueType());
        }
        //if we return false the same ImportJob will be retried
        return false;
      }
      elasticsearchBulkProcessor.persistZeebeRecords(importBatch);
      importPositionHolder.recordLatestLoadedPosition(getLastProcessedPosition());
      notifyImportListenersAsFinished(importBatch);
      return true;
    } catch (Throwable ex) {
      logger.error(ex.getMessage(), ex);
      notifyImportListenersAsFailed(importBatch);
      return false;
    }
  }

  private void markIndexAsDone() {
    previousPosition.setIndexName(null);
  }

  public void refreshPreviousIndex() throws IOException {
    RefreshRequest refreshRequest = new RefreshRequest(previousPosition.getIndexName());
    RefreshResponse refresh = zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    if (refresh.getFailedShards() > 0) {
      logger.warn("Unable to refresh the index: {}", previousPosition.getIndexName());
    }
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  public void recordLatestScheduledPosition() {
    importPositionHolder.recordLatestScheduledPosition(importBatch.getAliasName(), importBatch.getPartitionId(), getLastProcessedPosition());
  }

  public ImportPositionEntity getLastProcessedPosition() {
    if (lastProcessedPosition == null) {
      Long lastRecordPosition = importBatch.getLastProcessedPosition();
      if (lastRecordPosition != null) {
        lastProcessedPosition = ImportPositionEntity.createFrom(previousPosition, lastRecordPosition, importBatch.getLastRecordIndexName());
      } else {
        lastProcessedPosition = previousPosition;
      }
    }
    return lastProcessedPosition;
  }

  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null && previousPosition != null && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
    }
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
