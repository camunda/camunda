/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.camunda.optimize.data.generation.generators.client.SimpleEngineClient.DELAY_VARIABLE_NAME;

public class AnalysisTestingDataGenerator extends DataGenerator {
  private static final String DIAGRAM = "diagrams/analysis-testing.bpmn";

  public AnalysisTestingDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("freightOrdered", ThreadLocalRandom.current().nextDouble());
    variables.put("isTransferShipment", ThreadLocalRandom.current().nextDouble());
    variables.put("anotherEndEvent", ThreadLocalRandom.current().nextDouble());
    variables.put(DELAY_VARIABLE_NAME, ThreadLocalRandom.current().nextDouble() > 0.9);
    return variables;
  }
}
