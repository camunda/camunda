/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.instance.AwaitWorkflowInstanceResultMetadata;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public final class CreateWorkflowInstanceWithResultProcessor
    implements CommandProcessor<WorkflowInstanceCreationRecord> {

  private final CreateWorkflowInstanceProcessor createProcessor;
  private final MutableElementInstanceState elementInstanceState;
  private final AwaitWorkflowInstanceResultMetadata awaitResultMetadata =
      new AwaitWorkflowInstanceResultMetadata();

  private final CommandControlWithAwaitResult wrappedController =
      new CommandControlWithAwaitResult();

  private boolean shouldRespond;

  public CreateWorkflowInstanceWithResultProcessor(
      final CreateWorkflowInstanceProcessor createProcessor,
      final MutableElementInstanceState elementInstanceState) {
    this.createProcessor = createProcessor;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<WorkflowInstanceCreationRecord> command,
      final CommandControl<WorkflowInstanceCreationRecord> controller) {
    wrappedController.setCommand(command).setController(controller);
    createProcessor.onCommand(command, wrappedController);
    return shouldRespond;
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final WorkflowInstanceCreationRecord value) {
    createProcessor.afterAccept(commandWriter, stateWriter, key, intent, value);
  }

  private class CommandControlWithAwaitResult
      implements CommandControl<WorkflowInstanceCreationRecord> {
    TypedRecord<WorkflowInstanceCreationRecord> command;
    CommandControl<WorkflowInstanceCreationRecord> controller;

    public CommandControlWithAwaitResult setCommand(
        final TypedRecord<WorkflowInstanceCreationRecord> command) {
      this.command = command;
      return this;
    }

    public CommandControlWithAwaitResult setController(
        final CommandControl<WorkflowInstanceCreationRecord> controller) {
      this.controller = controller;
      return this;
    }

    @Override
    public long accept(final Intent newState, final WorkflowInstanceCreationRecord updatedValue) {
      shouldRespond = false;
      final ArrayProperty<StringValue> fetchVariables = command.getValue().fetchVariables();
      awaitResultMetadata
          .setRequestId(command.getRequestId())
          .setRequestStreamId(command.getRequestStreamId())
          .setFetchVariables(fetchVariables);

      elementInstanceState.setAwaitResultRequestMetadata(
          updatedValue.getWorkflowInstanceKey(), awaitResultMetadata);
      return controller.accept(newState, updatedValue);
    }

    @Override
    public void reject(final RejectionType type, final String reason) {
      shouldRespond = true;
      controller.reject(type, reason);
    }
  }
}
