/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;

public class BranchAnalysisDataGenerator extends DataGenerator {

  private static final String CALLER_DIAGRAM = "diagrams/call-branch-analysis.bpmn";
  private static final String CALLEE_DIAGRAM = "diagrams/branch_analysis_process.bpmn";

  public BranchAnalysisDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  @Override
  public void setInstanceCountToGenerate(int instanceCountToGenerate) {
    super.setInstanceCountToGenerate(instanceCountToGenerate/2);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(CALLER_DIAGRAM);
  }

  @Override
  public void run() {

    super.run();
  }

  @Override
  protected void deployAdditionalDiagrams() {
    super.deployAdditionalDiagrams();
    BpmnModelInstance bpmnModelInstance =
      readProcessDiagramAsInstance(CALLEE_DIAGRAM);
    engineClient.deployProcesses(bpmnModelInstance, 1, tenants);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    return new HashMap<>();
  }
}
