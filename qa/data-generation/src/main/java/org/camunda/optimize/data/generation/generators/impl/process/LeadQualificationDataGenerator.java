/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LeadQualificationDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/lead-qualification.bpmn";

  public LeadQualificationDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("qualified", ThreadLocalRandom.current().nextDouble());
    variables.put("sdrAvailable", ThreadLocalRandom.current().nextDouble());
    variables.put("landingPage", ThreadLocalRandom.current().nextDouble());
    variables.put("crmLeadAppearance", ThreadLocalRandom.current().nextDouble());
    variables.put("reviewDcOutcome", ThreadLocalRandom.current().nextDouble());
    variables.put("basicQualificationResult", ThreadLocalRandom.current().nextDouble());
    variables.put("responseResult", ThreadLocalRandom.current().nextDouble());
    variables.put("dcOutcome", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
