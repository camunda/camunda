/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Collections;

/**
 * Implementation of {@code TaskResultBuilder} that uses direct access to the stream. This
 * implementation is here to support a bridge for legacy code. Legacy code can first be shaped into
 * the interfaces defined in engine abstraction, and subseqeently the interfaces can be
 * re-implemented to allow for buffered writing to stream
 */
final class DirectTaskResultBuilder implements TaskResultBuilder {

  private final StreamProcessorContext context;
  private final LegacyTypedStreamWriter streamWriter;

  DirectTaskResultBuilder(final StreamProcessorContext context) {
    this.context = context;
    streamWriter = context.getLogStreamWriter();
    streamWriter.configureSourceContext(-1);
  }

  @Override
  public DirectTaskResultBuilder appendRecord(
      final long key,
      final RecordType type,
      final Intent intent,
      final RejectionType rejectionType,
      final String rejectionReason,
      final RecordValue value) {
    streamWriter.appendRecord(key, type, intent, rejectionType, rejectionReason, value);
    return this;
  }

  @Override
  public TaskResult build() {
    return new DirectProcessingResult(context, Collections.emptyList(), false);
  }
}
