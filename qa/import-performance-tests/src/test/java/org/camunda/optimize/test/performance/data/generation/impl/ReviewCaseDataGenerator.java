package org.camunda.optimize.test.performance.data.generation.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.performance.data.generation.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ReviewCaseDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/review-case.bpmn";

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getPathVariableNames() {
    Set<String> variableNames = new HashSet<>();
    variableNames.add("objectionPlausible");
    variableNames.add("moneyReceived");
    variableNames.add("recoursePossible");
    return variableNames;
  }

}
