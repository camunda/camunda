/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.zeebe.ImportValueType;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public interface RecordsReader extends Runnable {

  String PARTITION_ID_FIELD_NAME = ImportPositionIndex.PARTITION_ID;

  void readAndScheduleNextBatch(boolean autoContinue);

  ImportBatch readNextBatchBySequence(Long sequence, Long lastSequence) throws NoSuchIndexException;

  ImportBatch readNextBatchByPositionAndPartition(long positionFrom, Long positionTo) throws NoSuchIndexException;

  int getPartitionId();

  ImportValueType getImportValueType();

  BlockingQueue<Callable<Boolean>> getImportJobs();
}
