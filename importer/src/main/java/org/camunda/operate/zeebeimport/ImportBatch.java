/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;

import org.camunda.operate.zeebe.ImportValueType;
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

  private String lastRecordIndexName;

  private int finishedWiCount = 0;

  public ImportBatch(int partitionId, ImportValueType importValueType, List<Record> records, String lastRecordIndexName) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.records = records;
    this.lastRecordIndexName = lastRecordIndexName;
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

  public String getLastRecordIndexName() {
    return lastRecordIndexName;
  }

  public void setLastRecordIndexName(String lastRecordIndexName) {
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public long getLastProcessedPosition() {
    if (records != null && !records.isEmpty()) {
      return records.get(records.size() - 1).getPosition();
    } else {
      return 0;
    }
  }

  public String getAliasName() {
    return importValueType.getAliasTemplate();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ImportBatch that = (ImportBatch) o;

    if (partitionId != that.partitionId)
      return false;
    if (finishedWiCount != that.finishedWiCount)
      return false;
    if (importValueType != null ? !importValueType.equals(that.importValueType) : that.importValueType != null)
      return false;
    return records != null ? records.equals(that.records) : that.records == null;

  }

  @Override
  public int hashCode() {
    int result = partitionId;
    result = 31 * result + (importValueType != null ? importValueType.hashCode() : 0);
    result = 31 * result + (records != null ? records.hashCode() : 0);
    result = 31 * result + finishedWiCount;
    return result;
  }

}
