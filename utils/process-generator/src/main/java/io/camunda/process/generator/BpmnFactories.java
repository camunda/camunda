/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import io.camunda.process.generator.element.BpmnElementGeneratorFactory;
import io.camunda.process.generator.event.BpmnCatchEventGeneratorFactory;
import io.camunda.process.generator.parallel.BpmnParallelFlowGeneratorFactory;
import io.camunda.process.generator.template.BpmnTemplateGeneratorFactory;

public class BpmnFactories {

  private final BpmnCatchEventGeneratorFactory catchEventGeneratorFactory;
  private final BpmnElementGeneratorFactory elementGeneratorFactory;
  private final BpmnTemplateGeneratorFactory templateGeneratorFactory;
  private final BpmnParallelFlowGeneratorFactory parallelFlowGeneratorFactory;

  public BpmnFactories(final GeneratorContext generatorContent) {
    catchEventGeneratorFactory = new BpmnCatchEventGeneratorFactory(generatorContent);
    elementGeneratorFactory = new BpmnElementGeneratorFactory(generatorContent, this);
    templateGeneratorFactory = new BpmnTemplateGeneratorFactory(generatorContent, this);
    parallelFlowGeneratorFactory = new BpmnParallelFlowGeneratorFactory(generatorContent, this);
  }

  public BpmnCatchEventGeneratorFactory getCatchEventGeneratorFactory() {
    return catchEventGeneratorFactory;
  }

  public BpmnElementGeneratorFactory getElementGeneratorFactory() {
    return elementGeneratorFactory;
  }

  public BpmnTemplateGeneratorFactory getTemplateGeneratorFactory() {
    return templateGeneratorFactory;
  }

  public BpmnParallelFlowGeneratorFactory getParallelFlowGeneratorFactory() {
    return parallelFlowGeneratorFactory;
  }
}
