/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.variable;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.msgpack.spec.MsgpackReaderException;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;

public final class UpdateVariableDocumentProcessor
    implements TypedRecordProcessor<VariableDocumentRecord> {

  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final StateWriter stateWriter;

  public UpdateVariableDocumentProcessor(
      final ElementInstanceState elementInstanceState,
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final StateWriter stateWriter) {
    this.elementInstanceState = elementInstanceState;
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(
      final TypedRecord<VariableDocumentRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final VariableDocumentRecord value = record.getValue();

    final ElementInstance scope = elementInstanceState.getInstance(value.getScopeKey());
    if (scope == null || scope.isTerminating() || scope.isInFinalState()) {
      final String reason =
          String.format(
              "Expected to update variables for element with key '%d', but no such element was found",
              value.getScopeKey());
      streamWriter.appendRejection(record, RejectionType.NOT_FOUND, reason);
      responseWriter.writeRejectionOnCommand(record, RejectionType.NOT_FOUND, reason);
      return;
    }

    final long processDefinitionKey = scope.getValue().getProcessDefinitionKey();
    final long processInstanceKey = scope.getValue().getProcessInstanceKey();
    try {
      if (value.getUpdateSemantics() == VariableDocumentUpdateSemantic.LOCAL) {
        variableBehavior.mergeLocalDocument(
            scope.getKey(), processDefinitionKey, processInstanceKey, value.getVariablesBuffer());
      } else {
        variableBehavior.mergeDocument(
            scope.getKey(), processDefinitionKey, processInstanceKey, value.getVariablesBuffer());
      }
    } catch (final MsgpackReaderException e) {
      final String reason =
          String.format(
              "Expected document to be valid msgpack, but it could not be read: '%s'",
              e.getMessage());
      streamWriter.appendRejection(record, RejectionType.INVALID_ARGUMENT, reason);
      responseWriter.writeRejectionOnCommand(record, RejectionType.INVALID_ARGUMENT, reason);
      return;
    }

    final long key = keyGenerator.nextKey();

    stateWriter.appendFollowUpEvent(key, VariableDocumentIntent.UPDATED, value);
    responseWriter.writeEventOnCommand(key, VariableDocumentIntent.UPDATED, value, record);
  }
}
