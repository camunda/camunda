/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnExclusiveGatewayGenerator implements BpmnTemplateGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnExclusiveGatewayGenerator.class);

  private static final int MIN_AMOUNT_OF_BRANCHES = 2;
  private static final int MAX_AMOUNT_OF_BRANCHES = 3;

  private final GeneratorContext generatorContext;
  private final BpmnElementSequenceGenerator elementSequenceGenerator;

  public BpmnExclusiveGatewayGenerator(
      final GeneratorContext generatorContext,
      final BpmnElementSequenceGenerator elementSequenceGenerator) {
    this.generatorContext = generatorContext;
    this.elementSequenceGenerator = elementSequenceGenerator;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    final var amountOfBranches =
        generatorContext.getRandomNumber(MAX_AMOUNT_OF_BRANCHES) + MIN_AMOUNT_OF_BRANCHES;
    final var executionBranch = generatorContext.getRandomNumber(MAX_AMOUNT_OF_BRANCHES);

    final var forkingGatewayId = generatorContext.createNewId();
    final var joiningGatewayId = generatorContext.createNewId();

    LOG.debug(
        "Adding exclusive gateway {} with {} branches, joining at {}, execution branch {}",
        forkingGatewayId,
        amountOfBranches,
        joiningGatewayId,
        executionBranch);

    final var exclusiveGatewayBuilder = processBuilder.exclusiveGateway(forkingGatewayId);

    // Add the default flow
    // TODO generate more complex branches
    final AbstractFlowNodeBuilder<?, ?> joiningGatewayBuilder =
        elementSequenceGenerator
            .addElements(
                exclusiveGatewayBuilder
                    .defaultFlow()
                    .sequenceFlowId(generatorContext.createNewId()),
                executionBranch == 0 && generateExecutionPath)
            .sequenceFlowId(generatorContext.createNewId())
            .exclusiveGateway(joiningGatewayId);

    // Add other branches
    for (int index = 1; index < amountOfBranches; index++) {
      final var branchShouldGenerateExecutionPath =
          executionBranch == index && generateExecutionPath;
      final AbstractFlowNodeBuilder<?, ?> branchBuilder =
          exclusiveGatewayBuilder
              .sequenceFlowId(generatorContext.createNewId())
              .conditionExpression(String.valueOf(branchShouldGenerateExecutionPath));
      // TODO generate more complex branches
      elementSequenceGenerator
          .addElements(branchBuilder, branchShouldGenerateExecutionPath)
          .connectTo(joiningGatewayId);
    }

    return joiningGatewayBuilder;
  }
}
