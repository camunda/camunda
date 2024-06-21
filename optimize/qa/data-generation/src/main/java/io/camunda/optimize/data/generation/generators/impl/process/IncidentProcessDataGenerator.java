/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class IncidentProcessDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/incident-process.bpmn";

  public IncidentProcessDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final String[] incidentType = new String[]{"noIncident", "incidentToBeResolved", "incident"};
    final Map<String, Object> variables = new HashMap<>();
    variables.put(
        "incidentType", incidentType[ThreadLocalRandom.current().nextInt(0, incidentType.length)]);
    return variables;
  }

  @Override
  protected boolean createsIncidents() {
    return true;
  }
}
