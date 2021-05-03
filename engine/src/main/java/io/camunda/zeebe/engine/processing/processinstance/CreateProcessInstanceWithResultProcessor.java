/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public final class CreateProcessInstanceWithResultProcessor
    implements CommandProcessor<ProcessInstanceCreationRecord> {

  private final CreateProcessInstanceProcessor createProcessor;
  private final MutableElementInstanceState elementInstanceState;
  private final AwaitProcessInstanceResultMetadata awaitResultMetadata =
      new AwaitProcessInstanceResultMetadata();

  private final CommandControlWithAwaitResult wrappedController =
      new CommandControlWithAwaitResult();

  private boolean shouldRespond;

  public CreateProcessInstanceWithResultProcessor(
      final CreateProcessInstanceProcessor createProcessor,
      final MutableElementInstanceState elementInstanceState) {
    this.createProcessor = createProcessor;
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller) {
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
      final ProcessInstanceCreationRecord value) {
    createProcessor.afterAccept(commandWriter, stateWriter, key, intent, value);
  }

  private class CommandControlWithAwaitResult
      implements CommandControl<ProcessInstanceCreationRecord> {
    TypedRecord<ProcessInstanceCreationRecord> command;
    CommandControl<ProcessInstanceCreationRecord> controller;

    public CommandControlWithAwaitResult setCommand(
        final TypedRecord<ProcessInstanceCreationRecord> command) {
      this.command = command;
      return this;
    }

    public CommandControlWithAwaitResult setController(
        final CommandControl<ProcessInstanceCreationRecord> controller) {
      this.controller = controller;
      return this;
    }

    @Override
    public long accept(final Intent newState, final ProcessInstanceCreationRecord updatedValue) {
      shouldRespond = false;
      final ArrayProperty<StringValue> fetchVariables = command.getValue().fetchVariables();
      awaitResultMetadata
          .setRequestId(command.getRequestId())
          .setRequestStreamId(command.getRequestStreamId())
          .setFetchVariables(fetchVariables);

      elementInstanceState.setAwaitResultRequestMetadata(
          updatedValue.getProcessInstanceKey(), awaitResultMetadata);
      return controller.accept(newState, updatedValue);
    }

    @Override
    public void reject(final RejectionType type, final String reason) {
      shouldRespond = true;
      controller.reject(type, reason);
    }
  }
}
