/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;
import io.zeebe.exporter.api.record.Record;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value type of the records.
 */
public class ImportBatch {

  private int partitionId;

  private ImportValueType importValueType;

  private List<Record> records;

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

}
