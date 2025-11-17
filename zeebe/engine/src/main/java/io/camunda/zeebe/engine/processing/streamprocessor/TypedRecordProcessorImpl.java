/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.msgpack.MsgpackPropertySizeException;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public final class TypedRecordProcessorImpl<T extends UnifiedRecordValue>
    implements TypedRecordProcessor<T> {

  private final TypedRecordProcessor<T> wrappedProcessor;

  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public TypedRecordProcessorImpl(
      final TypedRecordProcessor<T> wrappedProcessor, final Writers writers) {
    this.wrappedProcessor = wrappedProcessor;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<T> command) {
    wrappedProcessor.processRecord(command);
  }

  @Override
  public ProcessingError tryHandleError(final TypedRecord<T> command, final Throwable error) {
    if (error instanceof MsgpackPropertySizeException) {
      rejectionWriter.appendRejection(command, RejectionType.INVALID_ARGUMENT, error.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_ARGUMENT, error.getMessage());
      return ProcessingError.EXPECTED_ERROR;
    }
    return wrappedProcessor.tryHandleError(command, error);
  }
}
