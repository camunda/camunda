/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;

import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.stripedexecutor.StripedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.zeebe.exporter.api.record.Record;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value type of the records.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportBatch implements StripedRunnable {

  private static final Logger logger = LoggerFactory.getLogger(ImportBatch.class);
  
  private int partitionId;

  private ImportValueType importValueType;

  private List<Record> records;

  private ImportListener importListener;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ImportPositionHolder importPositionHolder;


  public ImportBatch(int partitionId, ImportValueType importValueType, List<Record> records, ImportListener importListener) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.records = records;
    this.importListener = importListener;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public void setImportValueType(ImportValueType importValueType) {
    this.importValueType = importValueType;
  }

  public List<Record> getRecords() {
    return records;
  }

  public void setRecords(List<Record> records) {
    this.records = records;
  }

  public int getRecordsCount() {
    return records.size();
  }

  public void finished() {
    int imported = getRecordsCount();
    importListener.finished(imported);
  }

  public void failed() {
    int failed = getRecordsCount();
    importListener.failed(failed);
  }

  @Override
  public void run() {
    try {
      //do import
      elasticsearchBulkProcessor.persistZeebeRecords(getRecords(), getImportValueType());
      //record latest position
      final long lastProcessedPosition = getRecords().get(getRecordsCount() - 1).getPosition();
      importPositionHolder.recordLatestLoadedPosition(getImportValueType().getAliasTemplate(),
          getPartitionId(), lastProcessedPosition);
      finished();
    } catch (PersistenceException e) {
      logger.error(e.getMessage(), e);
      failed();
      throw new OperateRuntimeException(String.format("Import of batch [%s, %s] failed with an error: %s", getPartitionId(), e.getMessage()), e);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      failed();
      throw new OperateRuntimeException(String.format("Import of batch [%s, %s] failed with an error: %s", getPartitionId(), ex.getMessage()), ex);
    }
  }

  @Override
  public Object getStripe() {
    return String.format("%s-%s", getPartitionId(), getImportValueType()).intern();
  }

}
