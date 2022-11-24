/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;

public class EscalationEventBuilder extends AbstractBlockBuilder {
  private final boolean usesCallActivity;
  private final boolean usesInterruptingEscalationEvent;
  private final boolean usesEscalationEndEvent;
  private final String escalationCode;
  private final String joinGatewayId;
  private final String calledProcessId;
  private final String escalationProcessStartEventId;
  private final String escalationProcessEndEventId;
  private final String boundaryEscalationCatchEventId;
  private final String intermediateEscalationThrowEventId;
  private final ConstructionContext context;

  public EscalationEventBuilder(final ConstructionContext context) {
    super(context.getIdGenerator().nextId());

    this.context = context;

    final var random = context.getRandom();
    usesCallActivity =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_CALL_ACTIVITY_ESCALATION_EVENT;
    usesEscalationEndEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_ESCALATION_END_EVENT;
    usesInterruptingEscalationEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_INTERRUPTING_ESCALATION_EVENT;

    if (usesCallActivity) {
      calledProcessId = "process_child_" + elementId;
    } else {
      calledProcessId = "";
    }

    escalationCode = "escalation_code_" + getElementId();
    joinGatewayId = "boundary_join_" + getElementId();
    escalationProcessStartEventId = "escalation_throw_start_event_" + getElementId();
    escalationProcessEndEventId = "escalation_throw_end_event_" + getElementId();
    boundaryEscalationCatchEventId = "boundary_escalation_catch_event_" + getElementId();
    intermediateEscalationThrowEventId = "intermediate_escalation_throw_event_" + getElementId();
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    if (usesCallActivity) {
      buildChildProcess();
      return buildCallActivity(nodeBuilder);
    } else {
      return buildSubProcess(nodeBuilder);
    }
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();
    result.appendDirectSuccessor(new StepActivateBPMNElement(getElementId()));
    return result;
  }

  private AbstractFlowNodeBuilder<?, ?> buildSubProcess(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final AbstractFlowNodeBuilder<?, ?> subProcessStart =
        nodeBuilder
            .subProcess(getElementId())
            .embeddedSubProcess()
            .startEvent(escalationProcessStartEventId);

    final AbstractFlowNodeBuilder<?, ?> workInProgress = buildEscalationThrowEvent(subProcessStart);

    final SubProcessBuilder subProcessDoneBuilder = workInProgress.subProcessDone();

    return connectEscalationCatchEvent(subProcessDoneBuilder);
  }

  private AbstractFlowNodeBuilder<?, ?> buildCallActivity(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final AbstractFlowNodeBuilder<?, ?> workInProgress =
        nodeBuilder.callActivity(getElementId()).zeebeProcessId(calledProcessId);

    return connectEscalationCatchEvent(workInProgress);
  }

  private void buildChildProcess() {
    final AbstractFlowNodeBuilder<?, ?> childProcessStart =
        Bpmn.createExecutableProcess(calledProcessId).startEvent(escalationProcessStartEventId);

    final AbstractFlowNodeBuilder<?, ?> workInProgress =
        buildEscalationThrowEvent(childProcessStart);

    final BpmnModelInstance childModelInstance = workInProgress.done();
    context.addCalledChildProcess(childModelInstance);
  }

  /**
   * This method can be called from within the SubProcess generation method, or the CallActivity
   * child process generation method.
   */
  private AbstractFlowNodeBuilder<?, ?> buildEscalationThrowEvent(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    if (usesEscalationEndEvent) {
      return nodeBuilder.endEvent(escalationProcessEndEventId).escalation(escalationCode);
    } else {
      return nodeBuilder
          .intermediateThrowEvent(intermediateEscalationThrowEventId)
          .escalation(escalationCode)
          .endEvent(escalationProcessEndEventId);
    }
  }

  public AbstractFlowNodeBuilder<?, ?> connectEscalationCatchEvent(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final AbstractFlowNodeBuilder<?, ?> workInProgress;
    if (usesInterruptingEscalationEvent) {
      workInProgress = nodeBuilder.exclusiveGateway(joinGatewayId);
    } else {
      workInProgress = nodeBuilder.parallelGateway(joinGatewayId);
    }

    return workInProgress
        .moveToActivity(getElementId())
        .boundaryEvent(boundaryEscalationCatchEventId)
        .escalation(escalationCode)
        .cancelActivity(usesInterruptingEscalationEvent)
        .connectTo(joinGatewayId);
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new EscalationEventBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
