/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.VariableState;
import io.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class BpmnProcessResultSenderBehavior {

  private final ProcessInstanceResultRecord resultRecord = new ProcessInstanceResultRecord();

  private final ElementInstanceState elementInstanceState;
  private final VariableState variableState;
  private final TypedResponseWriter responseWriter;

  public BpmnProcessResultSenderBehavior(
      final MutableZeebeState zeebeState, final TypedResponseWriter responseWriter) {
    elementInstanceState = zeebeState.getElementInstanceState();
    variableState = zeebeState.getVariableState();
    this.responseWriter = responseWriter;
  }

  public void sendResult(final BpmnElementContext context) {

    if (context.getBpmnElementType() != BpmnElementType.PROCESS) {
      throw new BpmnProcessingException(
          context,
          "Expected to send the result of the process instance but was not called from the process element");
    }

    final AwaitProcessInstanceResultMetadata requestMetadata =
        elementInstanceState.getAwaitResultRequestMetadata(context.getProcessInstanceKey());

    if (requestMetadata != null) {
      sendResult(context, requestMetadata);
    }
  }

  private void sendResult(
      final BpmnElementContext context, final AwaitProcessInstanceResultMetadata requestMetadata) {

    final DirectBuffer variablesAsDocument = collectVariables(context, requestMetadata);

    resultRecord
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setVersion(context.getProcessVersion())
        .setVariables(variablesAsDocument);

    responseWriter.writeResponse(
        context.getProcessInstanceKey(),
        ProcessInstanceResultIntent.COMPLETED,
        resultRecord,
        ValueType.PROCESS_INSTANCE_RESULT,
        requestMetadata.getRequestId(),
        requestMetadata.getRequestStreamId());

    responseWriter.flush();
  }

  private DirectBuffer collectVariables(
      final BpmnElementContext context, final AwaitProcessInstanceResultMetadata requestMetadata) {

    final Set<DirectBuffer> variablesToCollect = new HashSet<>();
    requestMetadata
        .fetchVariables()
        .forEach(
            variable -> {
              final var variableName = cloneBuffer(variable.getValue());
              variablesToCollect.add(variableName);
            });

    if (variablesToCollect.isEmpty()) {
      // collect all process instance variables
      return variableState.getVariablesAsDocument(context.getProcessInstanceKey());

    } else {
      return variableState.getVariablesAsDocument(
          context.getProcessInstanceKey(), variablesToCollect);
    }
  }
}
