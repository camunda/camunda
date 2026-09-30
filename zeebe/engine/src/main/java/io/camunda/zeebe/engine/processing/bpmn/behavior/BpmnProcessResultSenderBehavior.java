/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class BpmnProcessResultSenderBehavior {

  private final ProcessInstanceResultRecord resultRecord = new ProcessInstanceResultRecord();

  private final ElementInstanceState elementInstanceState;
  private final VariableState variableState;
  private final AsyncRequestState asyncRequestState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public BpmnProcessResultSenderBehavior(
      final ProcessingState processingState,
      final TypedResponseWriter responseWriter,
      final StateWriter stateWriter) {
    elementInstanceState = processingState.getElementInstanceState();
    variableState = processingState.getVariableState();
    asyncRequestState = processingState.getAsyncRequestState();
    this.responseWriter = responseWriter;
    this.stateWriter = stateWriter;
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
    closeAwaitingRequest(context);
  }

  /** Forgets the request awaiting the result of the process instance, if there is one. */
  public void closeAwaitingRequest(final BpmnElementContext context) {
    asyncRequestState
        .findRequest(
            context.getProcessInstanceKey(),
            ValueType.PROCESS_INSTANCE_CREATION,
            ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT)
        .ifPresent(
            request ->
                stateWriter.appendFollowUpEvent(
                    request.key(), AsyncRequestIntent.PROCESSED, request.record()));
  }

  private void sendResult(
      final BpmnElementContext context, final AwaitProcessInstanceResultMetadata requestMetadata) {

    final DirectBuffer variablesAsDocument = collectVariables(context, requestMetadata);

    resultRecord
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setVersion(context.getProcessVersion())
        .setVariables(variablesAsDocument)
        .setTenantId(context.getTenantId())
        .setTags(context.getTags())
        .setBusinessId(context.getBusinessId());

    responseWriter.writeAcceptedResponse(
        context.getProcessInstanceKey(),
        ProcessInstanceResultIntent.COMPLETED,
        resultRecord,
        ValueType.PROCESS_INSTANCE_RESULT,
        requestMetadata.getRequestId(),
        requestMetadata.getRequestStreamId());
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
