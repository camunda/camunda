/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.msgpack.mapping.Mappings;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class IOMappingHelper {

  private final MsgPackMergeTool mergeTool = new MsgPackMergeTool(4096);

  private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
  private final DirectBuffer bufferView = new UnsafeBuffer();

  public <T extends ExecutableFlowNode> void applyOutputMappings(final BpmnStepContext<T> context) {
    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

    final T element = context.getElement();
    final WorkflowInstanceRecord record = context.getValue();
    final long elementInstanceKey = context.getKey();
    final long workflowKey = record.getWorkflowKey();
    final Mappings outputMappings = element.getOutputMappings();
    final var hasOutputMappings = !outputMappings.isEmpty();

    final DirectBuffer temporaryVariables =
        variablesState.getTemporaryVariables(elementInstanceKey);
    if (temporaryVariables != null) {
      if (hasOutputMappings) {
        variablesState.setVariablesLocalFromDocument(
            elementInstanceKey, workflowKey, temporaryVariables);
      } else {
        variablesState.setVariablesFromDocument(
            elementInstanceKey, workflowKey, temporaryVariables);
      }

      variablesState.removeTemporaryVariables(elementInstanceKey);
    }

    if (hasOutputMappings) {
      final DirectBuffer mergedVariables =
          mergeOutputVariables(variablesState, elementInstanceKey, outputMappings);

      variablesState.setVariablesFromDocument(
          getVariableScopeKey(context), workflowKey, mergedVariables);
    }
  }

  private DirectBuffer mergeOutputVariables(
      final VariablesState variablesState,
      final long elementInstanceKey,
      final Mappings outputMappings) {

    mergeTool.reset();

    // fetch the variables from the target mappings to allow merging with existing variables
    final DirectBuffer targetVariables =
        variablesState.getVariablesAsDocument(
            elementInstanceKey, outputMappings.getTargetVariableNames());

    // copy variables to not override them with next variable state access
    buffer.putBytes(0, targetVariables, 0, targetVariables.capacity());
    bufferView.wrap(buffer, 0, targetVariables.capacity());

    mergeTool.mergeDocument(bufferView);

    final DirectBuffer sourceVariables =
        variablesState.getVariablesAsDocument(
            elementInstanceKey, outputMappings.getSourceVariableNames());

    mergeTool.mergeDocument(sourceVariables, outputMappings.get());
    return mergeTool.writeResultToBuffer();
  }

  public <T extends ExecutableFlowNode> void applyInputMappings(final BpmnStepContext<T> context) {

    final T element = context.getElement();
    final Mappings mappings = element.getInputMappings();

    if (!mappings.isEmpty()) {
      mergeTool.reset();

      final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

      final DirectBuffer scopeVariables =
          variablesState.getVariablesAsDocument(
              getVariableScopeKey(context), mappings.getSourceVariableNames());

      mergeTool.mergeDocument(scopeVariables, mappings.get());
      final DirectBuffer mappedVariables = mergeTool.writeResultToBuffer();

      final long scopeKey = context.getKey();
      final long workflowKey = context.getValue().getWorkflowKey();
      context
          .getElementInstanceState()
          .getVariablesState()
          .setVariablesLocalFromDocument(scopeKey, workflowKey, mappedVariables);
    }
  }

  private long getVariableScopeKey(final BpmnStepContext<?> context) {
    final var elementInstanceKey = context.getKey();
    final var flowScopeKey = context.getValue().getFlowScopeKey();

    // an inner multi-instance activity needs to read from/write to its own scope
    // to access the input and output element variables
    final var isMultiInstanceActivity =
        context.getElementInstance().getMultiInstanceLoopCounter() > 0;
    return isMultiInstanceActivity ? elementInstanceKey : flowScopeKey;
  }
}
