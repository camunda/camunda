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

import static org.camunda.optimize.data.generation.generators.client.SimpleEngineClient.DELAY_VARIABLE_NAME;

public class InvoiceDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/invoice.bpmn";
  private static final String DMN_DIAGRAM = "diagrams/invoiceBusinessDecisions.dmn";

  public InvoiceDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected void deployAdditionalDiagrams() {
    super.deployAdditionalDiagrams();
    DmnModelInstance dmnModelInstance = readDmnTableAsInstance(DMN_DIAGRAM);
    engineClient.deployDecisionAndGetIds(dmnModelInstance, tenants);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    String[] invoiceType = new String[]{"day-to-day expense", "budget", "exceptional"};
    String[] invoiceCategory = new String[]{"Misc", "Travel Expenses", "Software License Costs"};
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("invoiceClassification", invoiceType[ThreadLocalRandom.current().nextInt(0, 3)]);
    variables.put("amount", ThreadLocalRandom.current().nextDouble(0, 2000));
    variables.put(
      "invoiceCategory",
      invoiceCategory[ThreadLocalRandom.current().nextInt(0, invoiceCategory.length)]
    );
    variables.put(DELAY_VARIABLE_NAME, ThreadLocalRandom.current().nextDouble() > 0.9);
    return variables;
  }

}
