/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

public final class ProcessInstanceCommandProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private final ProcessInstanceCommandHandlers commandHandlers;
  private final ElementInstanceState elementInstanceState;
  private final ProcessInstanceCommandContext context;

  public ProcessInstanceCommandProcessor(final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
    commandHandlers = new ProcessInstanceCommandHandlers();
    context = new ProcessInstanceCommandContext(elementInstanceState);
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    populateCommandContext(record, responseWriter, streamWriter);
    commandHandlers.handle(context);
  }

  private void populateCommandContext(
      final TypedRecord<ProcessInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    context.setRecord(record);
    context.setResponseWriter(responseWriter);
    context.setStreamWriter(streamWriter);

    final ElementInstance elementInstance = elementInstanceState.getInstance(record.getKey());
    context.setElementInstance(elementInstance);
  }
}
