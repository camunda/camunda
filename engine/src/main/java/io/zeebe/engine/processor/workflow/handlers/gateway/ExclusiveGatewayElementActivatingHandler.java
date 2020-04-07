/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.el.Expression;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.Optional;

public final class ExclusiveGatewayElementActivatingHandler<T extends ExecutableExclusiveGateway>
    extends ElementActivatingHandler<T> {

  private static final String NO_OUTGOING_FLOW_CHOSEN_ERROR =
      "Expected at least one condition to evaluate to true, or to have a default flow";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final ExpressionProcessor expressionProcessor;

  public ExclusiveGatewayElementActivatingHandler(final ExpressionProcessor expressionProcessor) {
    super(expressionProcessor);
    this.expressionProcessor = expressionProcessor;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ExecutableExclusiveGateway exclusiveGateway = context.getElement();
    final var sequenceFlow = getSequenceFlowWithFulfilledCondition(context, exclusiveGateway);

    if (sequenceFlow != null) {
      // sequence is taken when the gateway is completed
      deferSequenceFlowTaken(context, sequenceFlow);
      return true;
    }

    return false;
  }

  private ExecutableSequenceFlow getSequenceFlowWithFulfilledCondition(
      final BpmnStepContext<T> context, final ExecutableExclusiveGateway exclusiveGateway) {

    for (final ExecutableSequenceFlow sequenceFlow : exclusiveGateway.getOutgoingWithCondition()) {
      final Expression condition = sequenceFlow.getCondition();

      final Optional<Boolean> isFulfilled =
          expressionProcessor.evaluateBooleanExpression(condition, context);

      if (isFulfilled.isEmpty()) {
        // the condition evaluation failed and an incident is raised
        return null;

      } else if (isFulfilled.get()) {
        // the condition is fulfilled
        return sequenceFlow;
      }
    }

    // no condition is fulfilled - take the default flow if exists
    final var defaultFlow = exclusiveGateway.getDefaultFlow();
    if (defaultFlow != null) {
      return defaultFlow;

    } else {
      context.raiseIncident(ErrorType.CONDITION_ERROR, NO_OUTGOING_FLOW_CHOSEN_ERROR);
      return null;
    }
  }

  private void deferSequenceFlowTaken(
      final BpmnStepContext<T> context, final ExecutableSequenceFlow sequenceFlow) {
    record.wrap(context.getValue());
    record.setElementId(sequenceFlow.getId());
    record.setBpmnElementType(BpmnElementType.SEQUENCE_FLOW);

    context
        .getOutput()
        .deferRecord(context.getKey(), record, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }
}
