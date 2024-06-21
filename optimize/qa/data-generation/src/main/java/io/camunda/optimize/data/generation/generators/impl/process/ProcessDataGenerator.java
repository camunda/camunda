/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.data.generation.generators.DataGenerator;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public abstract class ProcessDataGenerator extends DataGenerator<BpmnModelInstance> {

  private static final String CORRELATION_VARIABLE_NAME = "correlatingVariable";
  private static final String CORRELATION_VALUE_PREFIX = "correlationValue_";

  public ProcessDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected void addCorrelatingVariable(final Map<String, Object> variables) {
    variables.put(getCorrelatingVariableName(), getCorrelatingValue());
  }

  protected String getBusinessKey() {
    return getCorrelatingValue();
  }

  protected String getCorrelatingVariableName() {
    return CORRELATION_VARIABLE_NAME;
  }

  protected String getCorrelatingValue() {
    return CORRELATION_VALUE_PREFIX + getStartedInstanceCount();
  }

  @Override
  protected List<String> deployDiagrams(final BpmnModelInstance instance) {
    return engineClient.deployProcesses(instance, nVersions, tenants);
  }

  @Override
  protected void startInstance(final String definitionId, final Map<String, Object> variables) {
    addCorrelatingVariable(variables);
    engineClient.startProcessInstance(definitionId, variables, getBusinessKey());
  }

  public BpmnModelInstance readProcessDiagramAsInstance(final String diagramPath) {
    final InputStream inputStream = ProcessDataGenerator.class.getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }
}
