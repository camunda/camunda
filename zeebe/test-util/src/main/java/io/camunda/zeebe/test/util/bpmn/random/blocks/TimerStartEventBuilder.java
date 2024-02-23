/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerStartEvent;
import java.time.Duration;
import java.util.Map;

/**
 * NOTE: as we cannot set variables on a timer start event - variables which may be necessary to
 * take the right execution path - every timer start event is followed by a service task where the
 * variables are set. These steps cannot be separated, as otherwise any execution which requires
 * variables will fail.
 */
public class TimerStartEventBuilder implements StartEventBlockBuilder {

  private static final String VARIABLES_JOB_TYPE_SUFFIX = "_variables";
  private static final String VARIABLES_ELEMENT_ID_SUFFIX = "_variables_task";
  private final String startEventId;
  private final Duration timeToAdd;

  public TimerStartEventBuilder(final ConstructionContext context) {
    final var idGenerator = context.getIdGenerator();
    startEventId = idGenerator.nextId();
    timeToAdd = Duration.ofMinutes(context.getRandom().nextInt(60) + 5L);
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildStartEvent(final ProcessBuilder processBuilder) {
    final String dateExpression = String.format("now() + duration(\"%s\")", timeToAdd);
    return processBuilder
        .startEvent(startEventId)
        .timerWithDateExpression(dateExpression)
        .serviceTask(
            startEventId + VARIABLES_ELEMENT_ID_SUFFIX,
            b -> b.zeebeJobType(startEventId + VARIABLES_JOB_TYPE_SUFFIX));
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(
      final String processId, final Map<String, Object> variables) {
    final ExecutionPathSegment pathSegment = new ExecutionPathSegment();
    pathSegment.appendDirectSuccessor(new StepTriggerTimerStartEvent(timeToAdd));
    pathSegment.appendDirectSuccessor(
        new StepActivateAndCompleteJob(
            startEventId + VARIABLES_JOB_TYPE_SUFFIX,
            startEventId + VARIABLES_ELEMENT_ID_SUFFIX,
            variables));
    return pathSegment;
  }
}
