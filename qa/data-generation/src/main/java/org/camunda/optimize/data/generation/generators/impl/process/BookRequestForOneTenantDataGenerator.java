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

public class BookRequestForOneTenantDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/book-request-1-tenant.bpmn";

  public BookRequestForOneTenantDataGenerator(final SimpleEngineClient engineClient,
                                              final Integer nVersions,
                                              final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected void generateTenants() {
    this.tenants = Lists.newArrayList("library");
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }

  @Override
  protected String[] getCorrelationNames() {
    return new String[]{"ReceivedBookRequest", "HoldBook", "DeclineHold"};
  }
}
