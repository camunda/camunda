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

public class BpmnExclusiveGatewayGenerator implements BpmnTemplateGenerator {

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
      final AbstractFlowNodeBuilder<?, ?> processBuilder) {

    final var amountOfBranches =
        generatorContext.getRandomNumber(MAX_AMOUNT_OF_BRANCHES) + MIN_AMOUNT_OF_BRANCHES;

    final var forkingGatewayId = generatorContext.createNewId();
    final var joiningGatewayId = generatorContext.createNewId();
    final var exclusiveGatewayBuilder = processBuilder.exclusiveGateway(forkingGatewayId);

    // Add the default flow
    // TODO generate more complex branches
    final AbstractFlowNodeBuilder<?, ?> joiningGatewayBuilder =
        elementSequenceGenerator
            .addElements(
                exclusiveGatewayBuilder
                    .defaultFlow()
                    .sequenceFlowId(generatorContext.createNewId()))
            .sequenceFlowId(generatorContext.createNewId())
            .exclusiveGateway(joiningGatewayId);

    // Add other branches
    for (int i = 0; i < amountOfBranches - 1; i++) {
      final AbstractFlowNodeBuilder<?, ?> branchBuilder =
          exclusiveGatewayBuilder
              .sequenceFlowId(generatorContext.createNewId())
              .conditionExpression("false");
      // TODO generate more complex branches
      elementSequenceGenerator.addElements(branchBuilder).connectTo(joiningGatewayId);
    }

    return joiningGatewayBuilder;
  }
}
