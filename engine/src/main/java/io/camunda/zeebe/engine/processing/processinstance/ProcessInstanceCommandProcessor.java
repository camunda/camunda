/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

public final class ProcessInstanceCommandProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private final ProcessInstanceCommandHandlers commandHandlers;
  private final ElementInstanceState elementInstanceState;
  private final ProcessInstanceCommandContext context;
  private final Builders builders;

  public ProcessInstanceCommandProcessor(
      final Builders builders, final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
    commandHandlers = new ProcessInstanceCommandHandlers();
    context = new ProcessInstanceCommandContext(elementInstanceState);
    this.builders = builders;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> record) {
    populateCommandContext(record);
    commandHandlers.handle(context);
  }

  private void populateCommandContext(final TypedRecord<ProcessInstanceRecord> record) {
    context.setRecord(record);
    context.setWriters(builders);

    final ElementInstance elementInstance = elementInstanceState.getInstance(record.getKey());
    context.setElementInstance(elementInstance);
  }
}
