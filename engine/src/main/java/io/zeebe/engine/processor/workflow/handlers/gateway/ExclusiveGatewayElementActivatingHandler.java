/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionException;
import io.zeebe.msgpack.el.JsonConditionInterpreter;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;

public class ExclusiveGatewayElementActivatingHandler<T extends ExecutableExclusiveGateway>
    extends ElementActivatingHandler<T> {
  private static final String NO_OUTGOING_FLOW_CHOSEN_ERROR =
      "Expected at least one condition to evaluate to true, or to have a default flow";
  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final JsonConditionInterpreter interpreter;

  public ExclusiveGatewayElementActivatingHandler() {
    this(new JsonConditionInterpreter());
  }

  public ExclusiveGatewayElementActivatingHandler(JsonConditionInterpreter interpreter) {
    super();
    this.interpreter = interpreter;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final WorkflowInstanceRecord value = context.getValue();

    final ExecutableSequenceFlow sequenceFlow;
    try {
      final ExecutableExclusiveGateway exclusiveGateway = context.getElement();
      final DirectBuffer variables = determineVariables(context, exclusiveGateway);

      sequenceFlow = getSequenceFlowWithFulfilledCondition(exclusiveGateway, variables);
    } catch (JsonConditionException e) {
      context.raiseIncident(ErrorType.CONDITION_ERROR, e.getMessage());
      return false;
    }

    if (sequenceFlow == null) {
      context.raiseIncident(ErrorType.CONDITION_ERROR, NO_OUTGOING_FLOW_CHOSEN_ERROR);
      return false;
    }

    deferSequenceFlowTaken(context, value, sequenceFlow);
    return true;
  }

  private DirectBuffer determineVariables(
      BpmnStepContext<T> context, ExecutableExclusiveGateway exclusiveGateway) {
    final List<ExecutableSequenceFlow> sequenceFlows = exclusiveGateway.getOutgoingWithCondition();

    final Set<DirectBuffer> actualNeededVariables = new HashSet<>();
    for (final ExecutableSequenceFlow seqFlow : sequenceFlows) {
      final CompiledJsonCondition compiledCondition = seqFlow.getCondition();
      final Set<DirectBuffer> variableNames = compiledCondition.getVariableNames();
      actualNeededVariables.addAll(variableNames);
    }

    return context
        .getElementInstanceState()
        .getVariablesState()
        .getVariablesAsDocument(context.getKey(), actualNeededVariables);
  }

  private void deferSequenceFlowTaken(
      BpmnStepContext<T> context,
      WorkflowInstanceRecord value,
      ExecutableSequenceFlow sequenceFlow) {
    record.wrap(value);
    record.setElementId(sequenceFlow.getId());
    record.setBpmnElementType(BpmnElementType.SEQUENCE_FLOW);

    context
        .getOutput()
        .deferRecord(context.getKey(), record, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  private ExecutableSequenceFlow getSequenceFlowWithFulfilledCondition(
      ExecutableExclusiveGateway exclusiveGateway, DirectBuffer variables) {
    final List<ExecutableSequenceFlow> sequenceFlows = exclusiveGateway.getOutgoingWithCondition();

    for (final ExecutableSequenceFlow sequenceFlow : sequenceFlows) {
      final CompiledJsonCondition compiledCondition = sequenceFlow.getCondition();
      final boolean isFulFilled = interpreter.eval(compiledCondition, variables);

      if (isFulFilled) {
        return sequenceFlow;
      }
    }

    return exclusiveGateway.getDefaultFlow();
  }
}
