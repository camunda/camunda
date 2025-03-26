/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public interface RecordsReader extends Runnable {

  String PARTITION_ID_FIELD_NAME = ImportPositionIndex.PARTITION_ID;

  void readAndScheduleNextBatch(boolean autoContinue);

  ImportBatch readNextBatchBySequence(Long sequence, Long lastSequence) throws NoSuchIndexException;

  ImportBatch readNextBatchByPositionAndPartition(long positionFrom, Long positionTo)
      throws NoSuchIndexException;

  int getPartitionId();

  ImportValueType getImportValueType();

  BlockingQueue<Callable<Boolean>> getImportJobs();

  /**
   * This method is based on Zeebe class
   * https://github.com/camunda/camunda/blob/cd37a352991e2fc763b7b1b0f5dc5b68ef7637e1/exporters/elasticsearch-exporter/src/main/java/io/camunda/zeebe/exporter/RecordSequence.java
   * which we don't have in classpath and which defines how `sequence` value is built.
   *
   * @param partitionId
   * @param counter
   * @return
   */
  default long sequence(final int partitionId, final long counter) {
    return ((long) partitionId << 51) + counter;
  }
}
