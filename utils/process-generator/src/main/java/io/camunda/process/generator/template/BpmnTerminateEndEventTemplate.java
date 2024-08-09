/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.BpmnFeatureType;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractEndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnTerminateEndEventTemplate implements BpmnTemplateGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnTerminateEndEventTemplate.class);

  private final GeneratorContext generatorContext;
  private final BpmnFactories bpmnFactories;

  public BpmnTerminateEndEventTemplate(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    final var elementId = "terminate_%s".formatted(generatorContext.createNewId());
    final var amountOfBranches = generatorContext.getRandomNumberOfBranches(2, 3);
    final var indexOfFlowWithTerminateEndEvent = generatorContext.getRandomNumber(amountOfBranches);

    LOG.debug(
        "Adding {} parallel flows, one with terminate end event {}", amountOfBranches, elementId);

    final IntPredicate shouldGenerateExecutionPathForFlow =
        i -> i == indexOfFlowWithTerminateEndEvent;

    final var parallelFlows =
        bpmnFactories
            .getParallelFlowGeneratorFactory()
            .getGenerator()
            .addFlows(
                amountOfBranches,
                processBuilder,
                generateExecutionPath,
                shouldGenerateExecutionPathForFlow);

    final var selectedFlow = parallelFlows.get(indexOfFlowWithTerminateEndEvent);

    selectedFlow.endEvent(elementId, AbstractEndEventBuilder::terminate);
    parallelFlows.stream()
        .filter(flow -> flow != selectedFlow)
        .forEach(AbstractFlowNodeBuilder::endEvent);

    generatorContext.decrementCurrentAmountOfBranches(amountOfBranches);

    // return any of the flows that does not have the terminate end event
    return parallelFlows.get(
        IntStream.range(0, amountOfBranches)
            .filter(i -> i != indexOfFlowWithTerminateEndEvent)
            .findAny()
            .orElseThrow());
  }

  @Override
  public boolean addsBranches() {
    return true;
  }

  @Override
  public boolean addsDepth() {
    return false;
  }

  @Override
  public BpmnFeatureType getFeature() {
    return BpmnFeatureType.TERMINATE_EVENT;
  }
}
