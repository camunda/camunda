/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
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

    final boolean goDeeper = random.nextInt(maxDepth) > currentDepth;

    if (goDeeper) {
      embeddedSubProcessBuilder =
          factory.createBlockSequenceBuilder(context.withIncrementedDepth());
    }
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final SubProcessBuilder subProcessBuilder = nodeBuilder.subProcess(subProcessId);

    AbstractFlowNodeBuilder<?, ?> workInProgress =
        subProcessBuilder.embeddedSubProcess().startEvent(subProcessStartEventId);

    if (embeddedSubProcessBuilder != null) {
      workInProgress = embeddedSubProcessBuilder.buildFlowNodes(workInProgress);
    }

    return workInProgress.endEvent(subProcessEndEventId).subProcessDone();
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    if (embeddedSubProcessBuilder != null) {
      result.append(embeddedSubProcessBuilder.findRandomExecutionPath(random));
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
