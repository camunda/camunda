/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;

public final class WorkflowInstanceCommandProcessor
    implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowInstanceCommandHandlers commandHandlers;
  private final WorkflowEngineState state;
  private final WorkflowInstanceCommandContext context;

  public WorkflowInstanceCommandProcessor(
      final WorkflowEngineState state, final KeyGenerator keyGenerator) {
    this.state = state;
    commandHandlers = new WorkflowInstanceCommandHandlers();
    final EventOutput output = new EventOutput(state, keyGenerator);
    context = new WorkflowInstanceCommandContext(output, state.getElementInstanceState());
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    populateCommandContext(record, responseWriter, streamWriter);
    commandHandlers.handle(context);
  }

  private void populateCommandContext(
      final TypedRecord<WorkflowInstanceRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    context.setRecord(record);
    context.setResponseWriter(responseWriter);
    context.setStreamWriter(streamWriter);

    final ElementInstance elementInstance =
        state.getElementInstanceState().getInstance(record.getKey());
    context.setElementInstance(elementInstance);
  }
}
