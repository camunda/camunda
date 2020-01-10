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
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.HashSet;
import java.util.Set;
import org.agrona.DirectBuffer;

public class IOMappingHelper {

  private final MsgPackMergeTool mergeTool = new MsgPackMergeTool(4096);

  public <T extends ExecutableFlowNode> void applyOutputMappings(final BpmnStepContext<T> context) {
    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

    final T element = context.getElement();
    final WorkflowInstanceRecord record = context.getValue();
    final long elementInstanceKey = context.getKey();
    final long workflowKey = record.getWorkflowKey();
    final Mapping[] outputMappings = element.getOutputMappings();
    final boolean hasOutputMappings = outputMappings.length > 0;

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
      mergeTool.reset();

      final DirectBuffer variables =
          determineVariables(variablesState, elementInstanceKey, outputMappings);

      mergeTool.mergeDocumentStrictly(variables, outputMappings);
      final DirectBuffer mergedVariables = mergeTool.writeResultToBuffer();

      variablesState.setVariablesFromDocument(
          getVariableScopeKey(context), workflowKey, mergedVariables);
    }
  }

  public <T extends ExecutableFlowNode> void applyInputMappings(final BpmnStepContext<T> context) {

    final T element = context.getElement();
    final Mapping[] mappings = element.getInputMappings();

    if (mappings.length > 0) {
      mergeTool.reset();

      final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

      final DirectBuffer scopeVariables =
          determineVariables(variablesState, getVariableScopeKey(context), mappings);

      mergeTool.mergeDocumentStrictly(scopeVariables, mappings);
      final DirectBuffer mappedVariables = mergeTool.writeResultToBuffer();

      final long scopeKey = context.getKey();
      final long workflowKey = context.getValue().getWorkflowKey();
      context
          .getElementInstanceState()
          .getVariablesState()
          .setVariablesLocalFromDocument(scopeKey, workflowKey, mappedVariables);
    }
  }

  private DirectBuffer determineVariables(
      final VariablesState variablesState,
      final long elementInstanceKey,
      final Mapping[] outputMappings) {
    final Set<DirectBuffer> variableNames = new HashSet<>();
    for (final Mapping m : outputMappings) {
      variableNames.add(m.getSource().getVariableName());
    }
    return variablesState.getVariablesAsDocument(elementInstanceKey, variableNames);
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
