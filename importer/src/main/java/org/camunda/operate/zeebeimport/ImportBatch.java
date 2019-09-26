/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.zeebe.protocol.record.Record;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value type of the records.
 */
public class ImportBatch {

  private static final Logger logger = LoggerFactory.getLogger(ImportBatch.class);
  
  private int partitionId;

  private ImportValueType importValueType;

  private List<Record> records;

  private int finishedWiCount = 0;

  public ImportBatch(int partitionId, ImportValueType importValueType, List<Record> records) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.records = records;
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

  public void incrementFinishedWiCount() {
    finishedWiCount++;
  }

  public int getFinishedWiCount() {
    return finishedWiCount;
  }

  protected void notifyImportListenersAsFinished(List<ImportListener> importListeners) {
    for (ImportListener importListener: importListeners) {
      importListener.finished(this);
    }
  }

  protected void notifyImportListenersAsFailed(List<ImportListener> importListeners) {
    for (ImportListener importListener: importListeners) {
      importListener.finished(this);
    }
  }
}
