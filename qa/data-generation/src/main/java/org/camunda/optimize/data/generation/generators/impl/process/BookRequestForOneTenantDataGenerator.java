/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;

public class BookRequestForOneTenantDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/book-request-1-tenant.bpmn";

  public BookRequestForOneTenantDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
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
