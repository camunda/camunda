/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgpackReaderException;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import org.agrona.DirectBuffer;

public final class UpdateVariableDocumentProcessor
    implements TypedRecordProcessor<VariableDocumentRecord> {

  private final ElementInstanceState elementInstanceState;
  private final KeyGenerator keyGenerator;
  private final VariableBehavior variableBehavior;
  private final Writers writers;

  public UpdateVariableDocumentProcessor(
      final ElementInstanceState elementInstanceState,
      final KeyGenerator keyGenerator,
      final VariableBehavior variableBehavior,
      final Writers writers) {
    this.elementInstanceState = elementInstanceState;
    this.keyGenerator = keyGenerator;
    this.variableBehavior = variableBehavior;
    this.writers = writers;
  }

  @Override
  public void processRecord(final TypedRecord<VariableDocumentRecord> record) {
    final VariableDocumentRecord value = record.getValue();

    final ElementInstance scope = elementInstanceState.getInstance(value.getScopeKey());
    if (scope == null || scope.isTerminating() || scope.isInFinalState()) {
      final String reason =
          String.format(
              "Expected to update variables for element with key '%d', but no such element was found",
              value.getScopeKey());
      writers.rejection().appendRejection(record, RejectionType.NOT_FOUND, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.NOT_FOUND, reason);
      return;
    }

    final long processDefinitionKey = scope.getValue().getProcessDefinitionKey();
    final long processInstanceKey = scope.getValue().getProcessInstanceKey();
    final DirectBuffer bpmnProcessId = scope.getValue().getBpmnProcessIdBuffer();
    try {
      if (value.getUpdateSemantics() == VariableDocumentUpdateSemantic.LOCAL) {
        variableBehavior.mergeLocalDocument(
            scope.getKey(),
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            value.getVariablesBuffer());
      } else {
        variableBehavior.mergeDocument(
            scope.getKey(),
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            value.getVariablesBuffer());
      }
    } catch (final MsgpackReaderException e) {
      final String reason =
          String.format(
              "Expected document to be valid msgpack, but it could not be read: '%s'",
              e.getMessage());
      writers.rejection().appendRejection(record, RejectionType.INVALID_ARGUMENT, reason);
      writers.response().writeRejectionOnCommand(record, RejectionType.INVALID_ARGUMENT, reason);
      return;
    }

    final long key = keyGenerator.nextKey();

    writers.state().appendFollowUpEvent(key, VariableDocumentIntent.UPDATED, value);
    writers.response().writeEventOnCommand(key, VariableDocumentIntent.UPDATED, value, record);
  }
}
