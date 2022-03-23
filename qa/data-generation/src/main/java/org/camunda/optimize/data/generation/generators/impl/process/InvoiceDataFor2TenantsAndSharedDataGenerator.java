/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class InvoiceDataFor2TenantsAndSharedDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/invoice-2-tenants-and-shared.bpmn";

  public InvoiceDataFor2TenantsAndSharedDataGenerator(final SimpleEngineClient engineClient,
                                                      final Integer nVersions,
                                                      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected void generateTenants() {
    this.tenants = Lists.newArrayList(null, "sales", "engineering");
  }

  @Override
  protected Map<String, Object> createVariables() {
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
