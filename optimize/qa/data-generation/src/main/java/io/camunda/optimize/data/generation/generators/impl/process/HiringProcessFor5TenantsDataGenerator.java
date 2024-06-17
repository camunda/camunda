/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import com.google.common.collect.Lists;
import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class HiringProcessFor5TenantsDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/hiring-process-5-tenants.bpmn";
  private static final String TASK_AUTOMATICALLY_ASSIGNED = "Task_automatically_assigned";
  private static final String TASK_SCREEN_PROCEED = "Task_screen_proceed";
  private static final String TASK_PHONE_PROCEED = "Task_phone_proceed";
  private static final String TASK_ONSITE_INTERVIEW = "Task_onsite_interview";
  private static final String TASK_MAKE_OFFER = "Task_make_offer";
  private static final String TASK_OFFER_ACCEPTED = "Task_offer_accepted";
  private static final String[] allVariableNames = {
    TASK_AUTOMATICALLY_ASSIGNED,
    TASK_SCREEN_PROCEED,
    TASK_PHONE_PROCEED,
    TASK_ONSITE_INTERVIEW,
    TASK_MAKE_OFFER,
    TASK_OFFER_ACCEPTED
  };

  public HiringProcessFor5TenantsDataGenerator(
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
  protected void generateTenants() {
    tenants = Lists.newArrayList("hr", "engineering", "marketing", "support", "csm");
  }

  @Override
  protected Map<String, Object> createVariables() {
    final Map<String, Object> variables = new HashMap<>();
    Arrays.stream(allVariableNames)
        .forEach(v -> variables.put(v, ThreadLocalRandom.current().nextDouble()));
    return variables;
  }
}
