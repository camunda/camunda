/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.element;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.execution.CompleteJobStep;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceTaskGenerator implements BpmnElementGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceTaskGenerator.class);

  private final GeneratorContext generatorContext;

  public ServiceTaskGenerator(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder, final boolean generateExecutionPath) {
    final String elementId = generatorContext.createNewId();

    LOG.debug("Adding service task with id {}", elementId);

    final String jobType = "task_" + elementId;

    final ServiceTaskBuilder serviceTaskBuilder = processBuilder.serviceTask();
    serviceTaskBuilder.id(elementId).name(elementId).zeebeJobType(jobType);

    if (generateExecutionPath) {
      generatorContext.addExecutionStep(new CompleteJobStep(elementId, jobType));
    }

    return serviceTaskBuilder;
  }
}
