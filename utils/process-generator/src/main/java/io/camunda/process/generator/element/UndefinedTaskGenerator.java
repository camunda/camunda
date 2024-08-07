/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndefinedTaskGenerator implements BpmnElementGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(UndefinedTaskGenerator.class);

  private final GeneratorContext generatorContext;

  public UndefinedTaskGenerator(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    final String elementId = generatorContext.createNewId();

    LOG.debug("Adding undefined task with id {}", elementId);

    return processBuilder.task(elementId);
  }
}
