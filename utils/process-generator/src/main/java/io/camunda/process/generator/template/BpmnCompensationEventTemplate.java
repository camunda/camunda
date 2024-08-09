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
import io.camunda.process.generator.element.BpmnElementGenerator;
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public class BpmnCompensationEventTemplate implements BpmnTemplateGenerator {

  public static final int ELEMENT_LIMIT = 3;
  private final GeneratorContext generatorContext;
  private final BpmnFactories bpmnFactories;

  public BpmnCompensationEventTemplate(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    final var compensationHandlerId = "compensation_handler_" + generatorContext.createNewId();
    final var compensationHandlerJobType = "job_" + compensationHandlerId;

    final BpmnTemplateGeneratorFactory templateGeneratorFactory =
        bpmnFactories.getTemplateGeneratorFactory();

    final BpmnElementGenerator elementGenerator =
        bpmnFactories.getElementGeneratorFactory().getGeneratorForActivityWithCompensationEvent();

    final var element = elementGenerator.addElement(processBuilder, generateExecutionPath);

    if (element instanceof final AbstractActivityBuilder<?, ?> activity) {
      // add a task with compensation handler
      activity
          .boundaryEvent()
          .compensation(
              compensation ->
                  compensation
                      .serviceTask(compensationHandlerId)
                      .zeebeJobType(compensationHandlerJobType));
    } else {
      throw new RuntimeException(
          "Can't attach a task with compensation handler to '%s'"
              .formatted(element.getClass().getSimpleName()));
    }

    final int numberOfElement = generatorContext.getRandomNumberOfBranches(1, ELEMENT_LIMIT);

    AbstractFlowNodeBuilder<?, ?> branch = element;

    for (int i = 0; i < numberOfElement; i++) {
      final BpmnTemplateGenerator branchGenerator = templateGeneratorFactory.getMiddleGenerator();
      branch = branchGenerator.addElements(branch, generateExecutionPath);
    }

    final BpmnElementGenerator catchEventGenerator =
        bpmnFactories.getElementGeneratorFactory().getGeneratorForCompensationEvent();
    catchEventGenerator.addElement(branch, false);

    return element;
  }

  @Override
  public boolean addsBranches() {
    return false;
  }

  @Override
  public boolean addsDepth() {
    return false;
  }

  @Override
  public BpmnFeatureType getFeature() {
    return BpmnFeatureType.COMPENSATION_EVENT;
  }
}
