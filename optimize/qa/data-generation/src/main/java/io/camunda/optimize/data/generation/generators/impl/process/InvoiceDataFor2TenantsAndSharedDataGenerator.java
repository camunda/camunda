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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class InvoiceDataFor2TenantsAndSharedDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/invoice-2-tenants-and-shared.bpmn";

  public InvoiceDataFor2TenantsAndSharedDataGenerator(
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
    tenants = Lists.newArrayList(null, "sales", "engineering");
  }

  @Override
  protected Map<String, Object> createVariables() {
    final String[] invoiceType = new String[] {"day-to-day expense", "budget", "exceptional"};
    final String[] invoiceCategory =
        new String[] {"Misc", "Travel Expenses", "Software License Costs"};
    final HashMap<String, Object> variables = new HashMap<>();
    variables.put("invoiceClassification", invoiceType[ThreadLocalRandom.current().nextInt(0, 3)]);
    variables.put("amount", ThreadLocalRandom.current().nextDouble(0, 2000));
    variables.put(
        "invoiceCategory",
        invoiceCategory[ThreadLocalRandom.current().nextInt(0, invoiceCategory.length)]);
    return variables;
  }
}
