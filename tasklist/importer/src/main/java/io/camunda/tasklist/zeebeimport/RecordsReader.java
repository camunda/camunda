/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.zeebe.ImportValueType;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public interface RecordsReader extends Runnable {
  int readAndScheduleNextBatch(boolean autoContinue);

  int readAndScheduleNextBatch();

  ImportBatch readNextBatchBySequence(final Long sequence) throws NoSuchIndexException;

  boolean tryToScheduleImportJob(final ImportJob importJob, final boolean skipPendingJob);

  int getPartitionId();

  ImportValueType getImportValueType();

  BlockingQueue<Callable<Boolean>> getImportJobs();

  ImportBatch readNextBatchByPositionAndPartition(long positionFrom, Long positionTo)
      throws NoSuchIndexException;

  ImportBatch readNextBatchBySequence(final Long fromSequence, final Long toSequence)
      throws NoSuchIndexException;
}
