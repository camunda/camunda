/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.apache.commons.lang3.RandomUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;

public class LeadQualificationWithIncidentDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/lead-qualification-with-incidents.bpmn";

  public LeadQualificationWithIncidentDataGenerator(final SimpleEngineClient engineClient,
                                                    final Integer nVersions,
                                                    final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("qualified", RandomUtils.nextDouble());
    variables.put("sdrAvailable", RandomUtils.nextDouble());
    variables.put("landingPage", RandomUtils.nextDouble());
    variables.put("crmLeadAppearance", RandomUtils.nextDouble());
    variables.put("reviewDcOutcome", RandomUtils.nextDouble());
    variables.put("basicQualificationResult", RandomUtils.nextDouble());
    variables.put("responseResult", RandomUtils.nextDouble());
    variables.put("dcOutcome", RandomUtils.nextDouble());
    return variables;
  }

  @Override
  protected boolean createsIncidents() {
    return true;
  }

}
