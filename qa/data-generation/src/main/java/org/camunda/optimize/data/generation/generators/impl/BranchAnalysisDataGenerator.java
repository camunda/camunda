package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BranchAnalysisDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/call-branch-analysis.bpmn";

  public BranchAnalysisDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  @Override
  public void setInstanceCountToGenerate(int instanceCountToGenerate) {
    super.setInstanceCountToGenerate(instanceCountToGenerate/2);
  }

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void run() {
    try {
      BpmnModelInstance bpmnModelInstance = readDiagramAsInstance("diagrams/branch_analysis_process.bpmn");
      engineClient.deployProcesses(bpmnModelInstance, 1);
    } catch (IOException e) {
      e.printStackTrace();
    }
    super.run();
  }

  public Set<String> getPathVariableNames() {
    return new HashSet<>();
  }
}
