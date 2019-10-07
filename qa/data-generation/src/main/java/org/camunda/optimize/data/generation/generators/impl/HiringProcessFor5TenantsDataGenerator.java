/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class HiringProcessFor5TenantsDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/hiring-process-5-tenants.bpmn";
  private static String TASK_AUTOMATICALLY_ASSIGNED = "Task_automatically_assigned";
  private static String TASK_SCREEN_PROCEED = "Task_screen_proceed";
  private static String TASK_PHONE_PROCEED = "Task_phone_proceed";
  private static String TASK_ONSITE_INTERVIEW = "Task_onsite_interview";
  private static String TASK_MAKE_OFFER = "Task_make_offer";
  private static String TASK_OFFER_ACCEPTED = "Task_offer_accepted";
  private static String[] allVariableNames = {TASK_AUTOMATICALLY_ASSIGNED, TASK_SCREEN_PROCEED, TASK_PHONE_PROCEED,
    TASK_ONSITE_INTERVIEW, TASK_MAKE_OFFER, TASK_OFFER_ACCEPTED};

  public HiringProcessFor5TenantsDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected void generateTenants() {
    this.tenants = Lists.newArrayList("hr", "engineering", "marketing", "support", "csm");
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    Arrays.stream(allVariableNames)
      .forEach(v -> variables.put(v, ThreadLocalRandom.current().nextDouble()));
    return variables;
  }

}
