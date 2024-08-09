/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.BpmnFeatureType;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnEndEventTemplate implements BpmnTemplateGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnEndEventTemplate.class);
  private final GeneratorContext generatorContext;

  public BpmnEndEventTemplate(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElements(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {

    LOG.debug("Adding regular end event");

    return processBuilder.endEvent(generatorContext.createNewId());
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
    return BpmnFeatureType.END_EVENT;
  }
}
