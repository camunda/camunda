/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ParallelGatewayBuilder;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelGatewayGenerator implements BpmnTemplateGenerator {

  public static final int BRANCH_LIMIT = 3;
  private static final Logger LOG = LoggerFactory.getLogger(ParallelGatewayGenerator.class);
  private final GeneratorContext generatorContext;
  private final BpmnFactories bpmnFactories;

  public ParallelGatewayGenerator(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    final String forkingElementId = generatorContext.createNewId();
    final String joiningElementId = generatorContext.createNewId();

    final ParallelGatewayBuilder forkingGateway =
        processBuilder.parallelGateway(forkingElementId).name(forkingElementId);

    final BpmnTemplateGeneratorFactory templateGeneratorFactory =
        bpmnFactories.getTemplateGeneratorFactory();

    // add first branch
    final BpmnTemplateGenerator generator = templateGeneratorFactory.getGenerator();
    final AbstractFlowNodeBuilder<?, ?> firstBranch =
        generator.addElements(forkingGateway, generateExecutionPath);

    final ParallelGatewayBuilder joiningGateway =
        firstBranch.parallelGateway(joiningElementId).name(joiningElementId);

    // add remaining branches
    final int numberOfBranches = 1 + generatorContext.getRandomNumber(BRANCH_LIMIT - 1);

    LOG.debug(
        "Adding parallel gateway with {} branches, forking element id: {}, joining element id: {}",
        numberOfBranches,
        forkingElementId,
        joiningElementId);

    IntStream.range(0, numberOfBranches)
        .forEach(
            i -> {
              final BpmnTemplateGenerator branchGenerator = templateGeneratorFactory.getGenerator();
              final AbstractFlowNodeBuilder<?, ?> branch =
                  branchGenerator.addElements(forkingGateway, generateExecutionPath);
              branch.connectTo(joiningElementId);
            });

    return joiningGateway;
  }
}
