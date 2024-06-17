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

public class ProcessRequestDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/process-request.bpmn";

  public ProcessRequestDataGenerator(
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
    variables.put("processAvailable", ThreadLocalRandom.current().nextDouble());
    variables.put("partnerIsNew", ThreadLocalRandom.current().nextDouble());
    variables.put("isBestehenderPartner", ThreadLocalRandom.current().nextDouble());
    variables.put("bestehendenPartnerAendern", ThreadLocalRandom.current().nextDouble());
    variables.put("isAdressaenderung", ThreadLocalRandom.current().nextDouble());
    variables.put("policeChanged", ThreadLocalRandom.current().nextDouble());
    variables.put("isSanierungBuendel", ThreadLocalRandom.current().nextDouble());
    variables.put("portalvertrag", ThreadLocalRandom.current().nextDouble());
    variables.put("isInkassoNummerFalschFehlt", ThreadLocalRandom.current().nextDouble());
    variables.put("errorOccured", ThreadLocalRandom.current().nextDouble());
    variables.put("processInformation", ThreadLocalRandom.current().nextDouble());
    variables.put("isVorversichererAnfrage", ThreadLocalRandom.current().nextDouble());
    variables.put("isAusstehendeDokumente", ThreadLocalRandom.current().nextDouble());
    variables.put("andereRollenVorhanden", ThreadLocalRandom.current().nextDouble());
    variables.put("abweichenderPZVorhanden", ThreadLocalRandom.current().nextDouble());
    return variables;
  }
}
