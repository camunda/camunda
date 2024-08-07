/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import io.camunda.process.generator.BpmnGenerator.GeneratedProcess;
import io.camunda.process.generator.template.BpmnTemplateGenerator;
import io.camunda.process.generator.template.BpmnTemplateGeneratorFactory;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.Definitions;

public class ProcessGenerator {

  private final String camundaVersion;
  private final GeneratorContext generatorContext;
  private final BpmnFactories bpmnFactories;

  public ProcessGenerator(
      final String camundaVersion,
      final GeneratorContext generatorContext,
      final BpmnFactories bpmnFactories) {
    this.camundaVersion = camundaVersion;
    this.generatorContext = generatorContext;
    this.bpmnFactories = bpmnFactories;
  }

  public GeneratedProcess generateProcess() {
    final String processId = "process_" + generatorContext.createNewId();
    AbstractFlowNodeBuilder<?, ?> processBuilder =
        Bpmn.createExecutableProcess(processId).name(processId).startEvent();

    final BpmnTemplateGeneratorFactory templateGeneratorFactory =
        bpmnFactories.getTemplateGeneratorFactory();

    final var templateLimit = 3;
    for (int i = 0; i < templateLimit; i++) {
      final BpmnTemplateGenerator templateGenerator = templateGeneratorFactory.getGenerator();
      processBuilder = templateGenerator.addElements(processBuilder, true);
    }

    final BpmnModelInstance process = processBuilder.endEvent().done();

    // modify the version so I can open the process in the Camunda Modeler
    final Definitions definitions = process.getDefinitions();
    definitions.setExporterVersion(camundaVersion);
    definitions.setAttributeValueNs(
        BpmnModelConstants.MODELER_NS, "executionPlatformVersion", camundaVersion);

    return new GeneratedProcess(process, generatorContext.getExecutionPath());
  }
}
