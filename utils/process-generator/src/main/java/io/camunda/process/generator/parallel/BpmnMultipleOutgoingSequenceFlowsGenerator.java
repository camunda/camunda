/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.parallel;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnMultipleOutgoingSequenceFlowsGenerator implements BpmnParallelFlowGenerator {

  private static final Logger LOG =
      LoggerFactory.getLogger(BpmnMultipleOutgoingSequenceFlowsGenerator.class);

  private final BpmnFactories bpmnFactories;

  public BpmnMultipleOutgoingSequenceFlowsGenerator(
      @SuppressWarnings("unused") final GeneratorContext generatorContext,
      final BpmnFactories bpmnFactories) {
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public List<? extends AbstractFlowNodeBuilder<?, ?>> addFlows(
      final int numberOfFlows,
      final AbstractFlowNodeBuilder<?, ?> processBuilder,
      final boolean generateExecutionPath,
      final IntPredicate shouldGenerateExecutionPathForFlow) {

    final var builder =
        bpmnFactories
            .getElementGeneratorFactory()
            .getGenerator()
            .addElement(processBuilder, generateExecutionPath);

    LOG.debug("Adding {} outgoing sequence flows to previous element", numberOfFlows);

    return IntStream.range(0, numberOfFlows)
        .mapToObj(
            i ->
                addSingleFlow(
                    builder, generateExecutionPath && shouldGenerateExecutionPathForFlow.test(i)))
        .toList();
  }

  private AbstractFlowNodeBuilder<?, ?> addSingleFlow(
      final AbstractFlowNodeBuilder<? extends AbstractFlowNodeBuilder<?, ?>, ?> builder,
      final boolean generateExecutionPath) {
    return bpmnFactories
        .getTemplateGeneratorFactory()
        .getGenerator()
        .addElements(builder, generateExecutionPath);
  }
}
