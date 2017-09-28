package org.camunda.optimize.test.performance.data.generation.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.performance.data.generation.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ExtendedOrderDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/extended-order-process.bpmn";

  private static final int nVersions = 20;
  private static final int totalInstanceCount = 5000;

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getPathVariableNames() {
    return new HashSet<>();
  }

}
