/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.instance;

import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.instance.AwaitWorkflowInstanceResultMetadata;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public class CreateWorkflowInstanceWithResultProcessor
    implements CommandProcessor<WorkflowInstanceCreationRecord> {

  private final CreateWorkflowInstanceProcessor createProcessor;
  private final ElementInstanceState elementInstanceState;
  private final AwaitWorkflowInstanceResultMetadata awaitResultMetadata =
      new AwaitWorkflowInstanceResultMetadata();

  private final CommandControlWithAwaitResult wrappedController =
      new CommandControlWithAwaitResult();

  private boolean shouldRespond;

  public CreateWorkflowInstanceWithResultProcessor(
      CreateWorkflowInstanceProcessor createProcessor, ElementInstanceState elementInstanceState) {
    this.createProcessor = createProcessor;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public boolean onCommand(
      TypedRecord<WorkflowInstanceCreationRecord> command,
      CommandControl<WorkflowInstanceCreationRecord> controller,
      TypedStreamWriter streamWriter) {
    wrappedController.setCommand(command).setController(controller);
    createProcessor.onCommand(command, wrappedController, streamWriter);
    return shouldRespond;
  }

  private class CommandControlWithAwaitResult
      implements CommandControl<WorkflowInstanceCreationRecord> {
    TypedRecord<WorkflowInstanceCreationRecord> command;
    CommandControl<WorkflowInstanceCreationRecord> controller;

    public CommandControlWithAwaitResult setCommand(
        TypedRecord<WorkflowInstanceCreationRecord> command) {
      this.command = command;
      return this;
    }

    public CommandControlWithAwaitResult setController(
        CommandControl<WorkflowInstanceCreationRecord> controller) {
      this.controller = controller;
      return this;
    }

    @Override
    public long accept(Intent newState, WorkflowInstanceCreationRecord updatedValue) {
      shouldRespond = false;
      awaitResultMetadata
          .setRequestId(command.getRequestId())
          .setRequestStreamId(command.getRequestStreamId());

      elementInstanceState.setAwaitResultRequestMetadata(
          updatedValue.getWorkflowInstanceKey(), awaitResultMetadata);
      return controller.accept(newState, updatedValue);
    }

    @Override
    public void reject(RejectionType type, String reason) {
      shouldRespond = true;
      controller.reject(type, reason);
    }
  }
}
