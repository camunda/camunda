/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.execution.CompleteUserTaskStep;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskGenerator implements BpmnElementGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskGenerator.class);

  private final GeneratorContext generatorContext;

  public UserTaskGenerator(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    final String elementId = generatorContext.createNewId();

    LOG.debug("Adding user task with id {}", elementId);

    final UserTaskBuilder userTaskBuilder = processBuilder.userTask().zeebeUserTask();
    userTaskBuilder.id(elementId).name(elementId);

    if (generateExecutionPath) {
      generatorContext.addExecutionStep(new CompleteUserTaskStep(elementId));
    }

    return userTaskBuilder;
  }
}
