/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class InvoiceDataFor2TenantsAndSharedDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/invoice-2-tenants-and-shared.bpmn";
  private static final String DMN_DIAGRAM = "diagrams/invoiceBusinessDecisions-2-tenants-and-shared.dmn";

  public InvoiceDataFor2TenantsAndSharedDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected void deployAdditionalDiagrams() {
    super.deployAdditionalDiagrams();
    DmnModelInstance dmnModelInstance = readDmnTableAsInstance(DMN_DIAGRAM);
    engineClient.deployDecisionAndGetId(dmnModelInstance, tenants);
  }

  @Override
  protected void generateTenants() {
    this.tenants = Lists.newArrayList(null, "sales", "engineering");
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    String[] invoiceType = new String[]{"day-to-day expense", "budget", "exceptional"};
    String[] invoiceCategory = new String[]{"Misc", "Travel Expenses", "Software License Costs"};
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("invoiceClassification", invoiceType[ThreadLocalRandom.current().nextInt(0, 3)]);
    variables.put("amount",ThreadLocalRandom.current().nextDouble(0, 2000));
    variables.put("invoiceCategory",
                  invoiceCategory[ThreadLocalRandom.current().nextInt(0, invoiceCategory.length)]
    );
    return variables;
  }

}
