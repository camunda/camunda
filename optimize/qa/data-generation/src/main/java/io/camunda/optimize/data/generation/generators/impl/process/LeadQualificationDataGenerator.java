/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class LeadQualificationDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/lead-qualification.bpmn";

  public LeadQualificationDataGenerator(
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
    final Map<String, Object> variables = new HashMap<>();
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
