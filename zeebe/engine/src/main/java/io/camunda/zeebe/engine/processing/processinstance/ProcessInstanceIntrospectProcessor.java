/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceIntrospectRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntrospectIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ProcessInstanceIntrospectProcessor
    implements TypedRecordProcessor<ProcessInstanceIntrospectRecord> {

  private final TypedEventWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;

  public ProcessInstanceIntrospectProcessor(
      final Writers writers, final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceIntrospectRecord> record) {
    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        key, ProcessInstanceIntrospectIntent.INTROSPECTED, record.getValue());
    responseWriter.writeEventOnCommand(
        key, ProcessInstanceIntrospectIntent.INTROSPECTED, record.getValue(), record);
  }
}
