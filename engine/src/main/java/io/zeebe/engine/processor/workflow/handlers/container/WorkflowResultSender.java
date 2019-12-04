/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.state.instance.AwaitWorkflowInstanceResultMetadata;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public class WorkflowResultSender implements WorkflowPostProcessor {

  @Override
  public void accept(final BpmnStepContext<ExecutableFlowElementContainer> context) {
    final long elementInstanceKey = context.getElementInstance().getKey();

    final AwaitWorkflowInstanceResultMetadata requestMetadata =
        context.getElementInstanceState().getAwaitResultRequestMetadata(elementInstanceKey);

    if (requestMetadata != null) {
      sendResult(context, elementInstanceKey, requestMetadata);
    }
  }

  private void sendResult(
      final BpmnStepContext<ExecutableFlowElementContainer> context,
      final long elementInstanceKey,
      final AwaitWorkflowInstanceResultMetadata requestMetadata) {

    final DirectBuffer variablesAsDocument =
        collectVariables(
            context.getElementInstanceState().getVariablesState(),
            requestMetadata,
            elementInstanceKey);

    final WorkflowInstanceResultRecord resultRecord = new WorkflowInstanceResultRecord();
    resultRecord
        .setWorkflowInstanceKey(context.getValue().getWorkflowInstanceKey())
        .setWorkflowKey(context.getValue().getWorkflowKey())
        .setVariables(variablesAsDocument)
        .setBpmnProcessId(context.getValue().getBpmnProcessId())
        .setVersion(context.getValue().getVersion());

    final TypedResponseWriter responseWriter = context.getOutput().getResponseWriter();
    responseWriter.writeResponse(
        context.getKey(),
        WorkflowInstanceResultIntent.COMPLETED,
        resultRecord,
        ValueType.WORKFLOW_INSTANCE_RESULT,
        requestMetadata.getRequestId(),
        requestMetadata.getRequestStreamId());

    context.getSideEffect().add(responseWriter::flush);
  }

  private DirectBuffer collectVariables(
      final VariablesState variablesState,
      final AwaitWorkflowInstanceResultMetadata requestMetadata,
      final long elementInstanceKey) {

    final Set<DirectBuffer> variablesToCollect = new HashSet<>();
    if (requestMetadata.fetchVariables().iterator().hasNext()) {
      requestMetadata
          .fetchVariables()
          .forEach(
              variable -> {
                final DirectBuffer name = BufferUtil.cloneBuffer(variable.getValue());
                variablesToCollect.add(name);
              });

      return variablesState.getVariablesAsDocument(elementInstanceKey, variablesToCollect);
    } else {
      return variablesState.getVariablesAsDocument(elementInstanceKey);
    }
  }
}
