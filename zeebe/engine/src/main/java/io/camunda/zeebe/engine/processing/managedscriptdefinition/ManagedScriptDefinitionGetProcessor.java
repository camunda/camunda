/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.managedscriptdefinition;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ManagedScriptDefinitionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public final class ManagedScriptDefinitionGetProcessor
    implements TypedRecordProcessor<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionState state;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ManagedScriptDefinitionGetProcessor(
      final MutableProcessingState processingState, final Writers writers) {
    state = processingState.getManagedScriptDefinitionState();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<ManagedScriptDefinitionRecord> command) {
    final var definition = state.getManagedScriptDefinition(command.getKey());
    if (definition == null) {
      final var reason =
          "Expected to get managed script definition '%d', but it was not found"
              .formatted(command.getKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.NOT_FOUND, reason);
      return;
    }
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ManagedScriptDefinitionIntent.GOT, definition, command);
  }
}
