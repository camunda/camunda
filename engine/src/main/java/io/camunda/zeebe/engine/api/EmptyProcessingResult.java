/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.engine.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.engine.api.records.RecordBatch;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;

public final class EmptyProcessingResult implements ProcessingResult {

  public static final ProcessingResult INSTANCE = new EmptyProcessingResult();
  private final ImmutableRecordBatch emptyRecordBatch;

  private EmptyProcessingResult() {
    emptyRecordBatch = RecordBatch.empty();
  }

  @Override
  public long writeRecordsToStream(final LogStreamBatchWriter logStreamBatchWriter) {
    return 0;
  }

  @Override
  public ImmutableRecordBatch getRecordBatch() {
    return emptyRecordBatch;
  }

  @Override
  public boolean writeResponse(final CommandResponseWriter commandResponseWriter) {
    return true;
  }

  @Override
  public boolean executePostCommitTasks() {
    return true;
  }
}
