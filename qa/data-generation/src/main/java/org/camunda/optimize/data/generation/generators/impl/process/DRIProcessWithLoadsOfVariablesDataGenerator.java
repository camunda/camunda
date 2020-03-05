/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.apache.commons.lang3.RandomUtils.nextInt;

public class DRIProcessWithLoadsOfVariablesDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "diagrams/process/dri-process.bpmn";
  private String[] reviewGatewayOptions = new String[]{"yes", "no"};

  public DRIProcessWithLoadsOfVariablesDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("doReview", reviewGatewayOptions[nextInt(0, reviewGatewayOptions.length)]);
    variables.put("loopCardinality", nextInt(1, 6));
    IntStream.range(0, 100)
      .forEach(i -> variables.put(random(15, true, false), nextInt()));
    return variables;
  }
}
