/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
import io.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.steps.StepEnterSubProcess;
import io.zeebe.test.util.bpmn.random.steps.StepTimeoutSubProcess;
import java.util.Random;

/**
 * Generates an embedded sub process. The embedded sub process contains either a sequence of random
 * blocks or a start event directly connected to the end event
 */
public class SubProcessBlockBuilder implements BlockBuilder {

  private BlockBuilder embeddedSubProcessBuilder;
  private final String subProcessId;
  private final String subProcessStartEventId;
  private final String subProcessEndEventId;
  private final String subProcessBoundaryTimerEventId;

  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryTimerEvent;

  public SubProcessBlockBuilder(final ConstructionContext context) {
    final Random random = context.getRandom();
    final IDGenerator idGenerator = context.getIdGenerator();
    final BlockSequenceBuilder.BlockSequenceBuilderFactory factory =
        context.getBlockSequenceBuilderFactory();
    final int maxDepth = context.getMaxDepth();
    final int currentDepth = context.getCurrentDepth();

    subProcessId = idGenerator.nextId();
    subProcessStartEventId = idGenerator.nextId();
    subProcessEndEventId = idGenerator.nextId();

    subProcessBoundaryTimerEventId = "boundary_timer_" + subProcessId;

    final boolean goDeeper = random.nextInt(maxDepth) > currentDepth;

    if (goDeeper) {
      embeddedSubProcessBuilder =
          factory.createBlockSequenceBuilder(context.withIncrementedDepth());
      hasBoundaryTimerEvent =
          random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_TIMER_EVENT;
    } else {
      hasBoundaryTimerEvent = false;
    }

    hasBoundaryEvents = hasBoundaryTimerEvent; // extend here
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final SubProcessBuilder subProcessBuilderStart = nodeBuilder.subProcess(subProcessId);

    AbstractFlowNodeBuilder<?, ?> workInProgress =
        subProcessBuilderStart.embeddedSubProcess().startEvent(subProcessStartEventId);

    if (embeddedSubProcessBuilder != null) {
      workInProgress = embeddedSubProcessBuilder.buildFlowNodes(workInProgress);
    }

    final var subProcessBuilderDone =
        workInProgress.endEvent(subProcessEndEventId).subProcessDone();

    AbstractFlowNodeBuilder result = subProcessBuilderDone;
    if (hasBoundaryEvents) {
      final String joinGatewayId = "join_" + subProcessId;
      final ExclusiveGatewayBuilder exclusiveGatewayBuilder =
          subProcessBuilderDone.exclusiveGateway(joinGatewayId);

      if (hasBoundaryTimerEvent) {
        result =
            ((SubProcessBuilder) exclusiveGatewayBuilder.moveToNode(subProcessId))
                .boundaryEvent(
                    subProcessBoundaryTimerEventId,
                    b -> b.timerWithDurationExpression(subProcessBoundaryTimerEventId))
                .connectTo(joinGatewayId);
      }
    }

    return result;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    if (hasBoundaryTimerEvent) {
      // set an infinite timer as default; this can be overwritten by the execution path chosen
      result.setVariableDefault(
          subProcessBoundaryTimerEventId, AbstractExecutionStep.VIRTUALLY_INFINITE.toString());
    }
    final var enterSubProcessStep = new StepEnterSubProcess(subProcessId);

    result.append(enterSubProcessStep);

    if (embeddedSubProcessBuilder == null) {
      return result;
    }

    final var internalExecutionPath = embeddedSubProcessBuilder.findRandomExecutionPath(random);

    if (internalExecutionPath.getScheduledSteps().isEmpty()) {
      return result;
    }

    if (!hasBoundaryEvents || random.nextBoolean()) {
      result.append(internalExecutionPath);
    } else {
      final int cutOffPoint =
          Math.min(1, random.nextInt(internalExecutionPath.getScheduledSteps().size()));

      for (int i = 0; i < cutOffPoint; i++) {
        result.append(internalExecutionPath.getSteps().get(i));
      }

      if (hasBoundaryTimerEvent) {
        result.append(
            new StepTimeoutSubProcess(subProcessId, subProcessBoundaryTimerEventId),
            enterSubProcessStep);
      } // extend here for other boundary events
    }
    return result;
  }

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new SubProcessBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return true;
    }
  }
}
