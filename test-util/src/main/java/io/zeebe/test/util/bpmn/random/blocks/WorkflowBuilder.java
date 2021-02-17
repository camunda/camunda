/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPath;
import io.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

public final class WorkflowBuilder {

  private static final List<Function<ConstructionContext, StartEventBlockBuilder>>
      START_EVENT_BUILDER_FACTORIES =
          List.of(NoneStartEventBuilder::new, MessageStartEventBuilder::new);

  private final BlockBuilder blockBuilder;
  private final StartEventBlockBuilder startEventBuilder;

  private final String processId;
  private final String endEventId;

  public WorkflowBuilder(final ConstructionContext context) {
    blockBuilder = context.getBlockSequenceBuilderFactory().createBlockSequenceBuilder(context);

    final var idGenerator = context.getIdGenerator();
    processId = "process_" + idGenerator.nextId();

    final var random = context.getRandom();
    final var startEventBuilderFactory =
        START_EVENT_BUILDER_FACTORIES.get(random.nextInt(START_EVENT_BUILDER_FACTORIES.size()));
    startEventBuilder = startEventBuilderFactory.apply(context);

    endEventId = idGenerator.nextId();
  }

  public BpmnModelInstance buildWorkflow() {

    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    AbstractFlowNodeBuilder<?, ?> workflowWorkInProgress =
        startEventBuilder.buildStartEvent(processBuilder);

    workflowWorkInProgress = blockBuilder.buildFlowNodes(workflowWorkInProgress);

    return workflowWorkInProgress.endEvent(endEventId).done();
  }

  public ExecutionPath findRandomExecutionPath(final Random random) {
    final var followingPath = blockBuilder.findRandomExecutionPath(random);
    final var startPath =
        startEventBuilder.findRandomExecutionPath(processId, followingPath.collectVariables());
    startPath.append(followingPath);

    return new ExecutionPath(processId, startPath);
  }
}
