/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import java.util.Optional;

public final class EmptyProcessingResult implements ProcessingResult {

  public static final ProcessingResult INSTANCE = new EmptyProcessingResult();
  private final ImmutableRecordBatch emptyRecordBatch;

  private EmptyProcessingResult() {
    emptyRecordBatch = RecordBatch.empty();
  }

  @Override
  public ImmutableRecordBatch getRecordBatch() {
    return emptyRecordBatch;
  }

  @Override
  public Optional<ProcessingResponse> getProcessingResponse() {
    return Optional.empty();
  }

  @Override
  public boolean executePostCommitTasks() {
    return true;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }
}
