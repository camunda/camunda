/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFactories;
import io.camunda.process.generator.BpmnFeature;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.element.BpmnElementGenerator;
import io.camunda.process.generator.event.BpmnCatchEventGenerator;
import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public class BoundaryEventTemplate implements BpmnTemplateGenerator {

  private final GeneratorContext generatorContext;
  private final BpmnFactories bpmnFactories;

  public BoundaryEventTemplate(
      final GeneratorContext generatorContext, final BpmnFactories bpmnFactories) {
    this.generatorContext = generatorContext;
    this.bpmnFactories = bpmnFactories;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    final String boundaryEventElementId = generatorContext.createNewId();
    final String endEventElementId = generatorContext.createNewId();

    final boolean shouldTriggerBoundaryEvent = generatorContext.getRandomBoolean();

    final BpmnElementGenerator elementGenerator =
        bpmnFactories.getElementGeneratorFactory().getGeneratorForActivityWithBoundaryEvent();

    final var element =
        elementGenerator.addElement(
            processBuilder, generateExecutionPath && !shouldTriggerBoundaryEvent);
    final var joiningGatewayId = generatorContext.createNewId();
    final var joiningGateway = element.exclusiveGateway(joiningGatewayId);

    if (element instanceof final AbstractActivityBuilder<?, ?> activity) {
      // add an interrupting boundary event
      final var boundaryEvent =
          activity
              .boundaryEvent(boundaryEventElementId)
              .name(boundaryEventElementId)
              .cancelActivity(true);

      final BpmnCatchEventGenerator catchEventGenerator =
          bpmnFactories.getCatchEventGeneratorFactory().getGenerator();
      catchEventGenerator.addEventDefinition(
          boundaryEventElementId,
          boundaryEvent,
          generatorContext,
          generateExecutionPath && shouldTriggerBoundaryEvent);

      final BpmnTemplateGenerator branchGenerator =
          bpmnFactories.getTemplateGeneratorFactory().getMiddleGenerator();
      final AbstractFlowNodeBuilder<?, ?> branch =
          branchGenerator.addElements(
              boundaryEvent, generateExecutionPath && shouldTriggerBoundaryEvent);

      branch.connectTo(joiningGatewayId);
    } else {
      throw new RuntimeException(
          "Can't attach a boundary event to '%s'".formatted(element.getClass().getSimpleName()));
    }

    return joiningGateway;
  }

  @Override
  public boolean addsBranches() {
    return true;
  }

  @Override
  public BpmnFeature getFeature() {
    return BpmnFeature.BOUNDARY_EVENT;
  }
}
