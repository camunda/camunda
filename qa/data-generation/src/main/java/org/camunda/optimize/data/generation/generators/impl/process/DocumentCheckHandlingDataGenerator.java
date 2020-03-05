/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class DocumentCheckHandlingDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "diagrams/process/document-check-handling.bpmn";

  public DocumentCheckHandlingDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("approved", ThreadLocalRandom.current().nextDouble());
    return variables;
  }


  public Set<String> getPathVariableNames() {
    Set<String> variableNames = new HashSet<>();
    variableNames.add("approved");
    return variableNames;
  }

}
