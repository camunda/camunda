/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.template;

import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.element.BpmnElementGeneratorFactory;

public class BpmnTemplateGeneratorFactory {

  private final BpmnElementSequenceGenerator elementSequenceGenerator;

  public BpmnTemplateGeneratorFactory(
      final GeneratorContext generatorContext,
      final BpmnElementGeneratorFactory elementGeneratorFactory) {
    elementSequenceGenerator =
        new BpmnElementSequenceGenerator(generatorContext, elementGeneratorFactory);
  }

  public BpmnTemplateGenerator getGenerator() {
    return elementSequenceGenerator;
  }
}
