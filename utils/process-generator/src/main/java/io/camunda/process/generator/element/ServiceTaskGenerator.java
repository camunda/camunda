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
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;

public class ServiceTaskGenerator implements BpmnElementGenerator {

  private final GeneratorContext generatorContext;

  public ServiceTaskGenerator(final GeneratorContext generatorContext) {
    this.generatorContext = generatorContext;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> addElement(
      final AbstractFlowNodeBuilder<?, ?> processBuilder) {
    final String elementId = generatorContext.createNewId();
    final String jobType = "task_" + elementId;

    final ServiceTaskBuilder serviceTaskBuilder = processBuilder.serviceTask();
    serviceTaskBuilder.id(elementId).name(elementId).zeebeJobType(jobType);

    return serviceTaskBuilder;
  }
}
