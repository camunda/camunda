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
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class BranchAnalysisDataGenerator extends ProcessDataGenerator {

  private static final String CALLER_DIAGRAM = "/diagrams/process/call-branch-analysis.bpmn";
  private static final String CALLEE_DIAGRAM = "/diagrams/process/branch_analysis_process.bpmn";

  public BranchAnalysisDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  public void setInstanceCountToGenerate(final int instanceCountToGenerate) {
    super.setInstanceCountToGenerate(instanceCountToGenerate / 2);
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(CALLER_DIAGRAM);
  }

  @Override
  protected void deployAdditionalDiagrams() {
    super.deployAdditionalDiagrams();
    final BpmnModelInstance bpmnModelInstance = readProcessDiagramAsInstance(CALLEE_DIAGRAM);
    engineClient.deployProcesses(bpmnModelInstance, 1, tenants);
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }
}
