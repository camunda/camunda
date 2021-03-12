/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import static io.zeebe.test.util.bpmn.random.blocks.IntermediateMessageCatchEventBlockBuilder.CORRELATION_KEY_VALUE;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPath;
import io.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import io.zeebe.test.util.bpmn.random.blocks.IntermediateMessageCatchEventBlockBuilder.StepPublishMessage;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public final class ProcessBuilder {

  private static final List<Function<ConstructionContext, StartEventBlockBuilder>>
      START_EVENT_BUILDER_FACTORIES =
          List.of(NoneStartEventBuilder::new, MessageStartEventBuilder::new);

  private final BlockBuilder blockBuilder;
  private final StartEventBlockBuilder startEventBuilder;

  private final String processId;
  private final String endEventId;
  private final boolean hasEventSubProcess;
  private String eventSubProcessId = null;
  private boolean isEventSubProcessInterrupting;
  private String eventSubProcessMessageName;

  public ProcessBuilder(final ConstructionContext context) {
    blockBuilder = context.getBlockSequenceBuilderFactory().createBlockSequenceBuilder(context);

    final var idGenerator = context.getIdGenerator();
    processId = "process_" + idGenerator.nextId();
    hasEventSubProcess = initEventSubProcess(context, idGenerator);
    final var random = context.getRandom();
    final var startEventBuilderFactory =
        START_EVENT_BUILDER_FACTORIES.get(random.nextInt(START_EVENT_BUILDER_FACTORIES.size()));
    startEventBuilder = startEventBuilderFactory.apply(context);

    endEventId = idGenerator.nextId();
  }

  private boolean initEventSubProcess(
      final ConstructionContext context,
      final io.zeebe.test.util.bpmn.random.IDGenerator idGenerator) {
    final boolean hasEventSubProcess = context.getRandom().nextBoolean();
    if (hasEventSubProcess) {
      eventSubProcessId = "eventSubProcess_" + idGenerator.nextId();
      isEventSubProcessInterrupting = context.getRandom().nextBoolean();
      eventSubProcessMessageName = "message_" + eventSubProcessId;
    }
    return hasEventSubProcess;
  }

  public BpmnModelInstance buildProcess() {

    final io.zeebe.model.bpmn.builder.ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess(processId);

    if (hasEventSubProcess) {
      buildEventSubProcess(processBuilder);
    }

    AbstractFlowNodeBuilder<?, ?> processWorkInProgress =
        startEventBuilder.buildStartEvent(processBuilder);

    processWorkInProgress = blockBuilder.buildFlowNodes(processWorkInProgress);

    return processWorkInProgress.endEvent(endEventId).done();
  }

  private void buildEventSubProcess(
      final io.zeebe.model.bpmn.builder.ProcessBuilder processBuilder) {
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
                // See https://github.com/camunda-cloud/zeebe/issues/4099
                b.name(eventSubProcessMessageName)
                    .zeebeCorrelationKeyExpression('\"' + CORRELATION_KEY_VALUE + '\"'))
        .endEvent("end_event_" + eventSubProcessId);
  }

  public ExecutionPath findRandomExecutionPath(final Random random) {
    final var followingPath = blockBuilder.findRandomExecutionPath(random);

    if (hasEventSubProcess) {
      final var shouldTriggerEventSubProcess = random.nextBoolean();
      if (shouldTriggerEventSubProcess) {
        executionPathForEventSubProcess(random, followingPath);
      }
    }

    final var startPath =
        startEventBuilder.findRandomExecutionPath(processId, followingPath.collectVariables());
    startPath.append(followingPath);

    return new ExecutionPath(processId, startPath);
  }

  private void executionPathForEventSubProcess(
      final Random random,
      final io.zeebe.test.util.bpmn.random.ExecutionPathSegment followingPath) {
    // We don't want to trigger the event sub process at the end of the process instance execution,
    // which is the reason why we decrement by one. With that we avoid a race condition, which can
    // happen on triggering the event sub process and completing the process instance.
    final var size = followingPath.getSteps().size() - 1;

    if (size < 1) {
      // empty execution path
      // We will not add here an event sub process execution, since the likelihood that the event
      // sub process is triggered in time, before the process is completed, is quite low.
      // This can cause flaky tests.
      return;
    }

    final var index = random.nextInt(size);
    if (isEventSubProcessInterrupting) {
      // if it is interrupting we remove the other execution path
      followingPath.replace(index, new StepPublishMessage(eventSubProcessMessageName));
    } else {
      followingPath.insert(index, new StepPublishMessage(eventSubProcessMessageName));
    }
  }
}
