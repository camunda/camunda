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

public class OrderConfirmationDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/order-confirmation.bpmn";

  public OrderConfirmationDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("isSubFileLevel", ThreadLocalRandom.current().nextDouble());
    variables.put("orderConfirmationProductionShipperRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("shipperContactInformationValid", ThreadLocalRandom.current().nextDouble());
    variables.put("orderConfirmationProductionConsigneeRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("consigneeContactInformationValid", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
