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
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPath;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepStartProcessInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public final class ProcessBuilder {

  private static final String EVENT_SUBPROCESS_CORRELATION_KEY_VALUE = "default_correlation_key";

  private static final List<Function<ConstructionContext, StartEventBlockBuilder>>
      START_EVENT_BUILDER_FACTORIES =
          List.of(
              NoneStartEventBuilder::new,
              MessageStartEventBuilder::new,
              TimerStartEventBuilder::new);

  private final BlockSequenceBuilder blockBuilder;
  private final StartEventBlockBuilder startEventBuilder;
  private final List<BpmnModelInstance> calledChildModelInstances = new ArrayList<>();

  private final String processId;
  private final String endEventId;
  private final boolean hasEventSubProcess;
  private String eventSubProcessId;
  private boolean isEventSubProcessInterrupting;
  private String eventSubProcessMessageName;

  public ProcessBuilder(final ConstructionContext context) {
    final ConstructionContext processContext =
        context.withAddCalledChildProcessCallback(this::onAddCalledChildProcess);

    blockBuilder =
        processContext.getBlockSequenceBuilderFactory().createBlockSequenceBuilder(processContext);

    final var idGenerator = processContext.getIdGenerator();
    processId = "process_" + idGenerator.nextId();
    hasEventSubProcess = initEventSubProcess(processContext, idGenerator);
    final var random = processContext.getRandom();
    final var startEventBuilderFactory =
        START_EVENT_BUILDER_FACTORIES.get(random.nextInt(START_EVENT_BUILDER_FACTORIES.size()));
    startEventBuilder = startEventBuilderFactory.apply(processContext);

    endEventId = idGenerator.nextId();
  }

  public void onAddCalledChildProcess(final BpmnModelInstance childModelInstance) {
    calledChildModelInstances.add(childModelInstance);
  }

  private boolean initEventSubProcess(
      final ConstructionContext context,
      final io.camunda.zeebe.test.util.bpmn.random.IDGenerator idGenerator) {
    final boolean hasEventSubProcess = context.getRandom().nextBoolean();
    if (hasEventSubProcess) {
      eventSubProcessId = "eventSubProcess_" + idGenerator.nextId();
      isEventSubProcessInterrupting = context.getRandom().nextBoolean();
      eventSubProcessMessageName = "message_" + eventSubProcessId;
    }
    return hasEventSubProcess;
  }

  /**
   * @return the build process and any potentially called child processes
   */
  public List<BpmnModelInstance> buildProcess() {
    final var result = new ArrayList<BpmnModelInstance>();

    final io.camunda.zeebe.model.bpmn.builder.ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess(processId);

    if (hasEventSubProcess) {
      buildEventSubProcess(processBuilder);
    }

    AbstractFlowNodeBuilder<?, ?> processWorkInProgress =
        startEventBuilder.buildStartEvent(processBuilder);

    processWorkInProgress = blockBuilder.buildFlowNodes(processWorkInProgress);

    final var rootModelInstance = processWorkInProgress.endEvent(endEventId).done();
    result.add(rootModelInstance);
    result.addAll(calledChildModelInstances);
    return result;
  }

  private void buildEventSubProcess(
      final io.camunda.zeebe.model.bpmn.builder.ProcessBuilder processBuilder) {
    processBuilder
        .eventSubProcess(eventSubProcessId)
        .startEvent("start_event_" + eventSubProcessId)
        .interrupting(isEventSubProcessInterrupting)
        .message(
            b ->
                // When we have a message start event then variables are not correctly copied,
                // which will not trigger the event sub process then. We use here a static value to
                // trigger the event sub process.
                //
                // See https://github.com/camunda/zeebe/issues/4099
                b.name(eventSubProcessMessageName)
                    .zeebeCorrelationKeyExpression(
                        '\"' + EVENT_SUBPROCESS_CORRELATION_KEY_VALUE + '\"'))
        .endEvent("end_event_" + eventSubProcessId);
  }

  public ExecutionPath findRandomExecutionPath(final Random random) {
    // Give processes a 1/3 chance to start in a random spot
    final var startAnywhere = random.nextInt(3) == 0;

    final ExecutionPathContext context;
    if (startAnywhere) {
      final var possibleStartingElementIds = blockBuilder.getPossibleStartingElementIds();
      final int startBlockIndex = random.nextInt(possibleStartingElementIds.size());
      final var startAtElementId = possibleStartingElementIds.get(startBlockIndex);
      context = new ExecutionPathContext(startAtElementId, random);
    } else {
      context = new ExecutionPathContext(blockBuilder.getElementId(), random);
    }

    final ExecutionPathSegment followingPath = blockBuilder.findRandomExecutionPath(context);

    if (hasEventSubProcess) {
      final var shouldTriggerEventSubProcess = random.nextBoolean();
      if (shouldTriggerEventSubProcess) {
        executionPathForEventSubProcess(random, followingPath);
      }
    }

    final ExecutionPathSegment startPath;
    if (startAnywhere) {
      startPath = new ExecutionPathSegment();
      startPath.appendDirectSuccessor(
          new StepStartProcessInstance(
              processId, followingPath.collectVariables(), context.getStartElementIds()));
    } else {
      startPath =
          startEventBuilder.findRandomExecutionPath(processId, followingPath.collectVariables());
    }
    startPath.append(followingPath);

    return new ExecutionPath(processId, startPath);
  }

  private void executionPathForEventSubProcess(
      final Random random,
      final io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment followingPath) {
    // We don't want to trigger the event sub process at the end of the process instance execution,
    // which is the reason why we decrement by one. With that we avoid a race condition, which can
    // happen on triggering the event sub process and completing the process instance.
    final var size = followingPath.getSteps().size() - 1;

    if (size < 1 || !followingPath.canBeInterrupted()) {
      // empty execution path or path which cannot be interrupted
      // We will not add here an event sub process execution, since the likelihood that the event
      // sub process is triggered in time, before the process is completed, is quite low.
      // This can cause flaky tests.
      return;
    }

    final var executionStep =
        new StepPublishMessage(
            eventSubProcessMessageName, "", EVENT_SUBPROCESS_CORRELATION_KEY_VALUE);

    if (isEventSubProcessInterrupting) {
      // if it is interrupting we remove the other execution path
      followingPath.cutAtRandomPosition(random);
      followingPath.appendDirectSuccessor(executionStep);
    } else {
      followingPath.insertExecutionStep(random, executionStep);
    }
  }
}
