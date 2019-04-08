/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DmnTableDataGenerator extends DataGenerator {

  private static final String DIAGRAM_THAT_CALLS_DMN = "diagrams/dmn-table-process.bpmn";
  private static final String DMN_DIAGRAM = "diagrams/decide-dish.dmn";

  public DmnTableDataGenerator(SimpleEngineClient simpleEngineClient) {
    super(simpleEngineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM_THAT_CALLS_DMN);
  }

  @Override
  protected void deployAdditionalDiagrams() {
    super.deployAdditionalDiagrams();
    DmnModelInstance dmnModelInstance = readDmnTableAsInstance(DMN_DIAGRAM);
    engineClient.deployDecisionAndGetId(dmnModelInstance);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    String[] seasons = new String[] {"Fall", "Summer", "Spring", "Winter"};
    Map<String, Object> variables = new HashMap<>();
    variables.put("guestCount", ThreadLocalRandom.current().nextInt(0,11));
    variables.put("season", seasons[ThreadLocalRandom.current().nextInt(0, 4)]);
    variables.put("guestsWithChildren", ThreadLocalRandom.current().nextBoolean());
    return variables;
  }

}
