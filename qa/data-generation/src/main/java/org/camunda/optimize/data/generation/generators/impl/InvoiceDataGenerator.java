package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.SimpleEngineClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class InvoiceDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/invoice.bpmn";

  public InvoiceDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

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
    variableNames.add("approved");
    variableNames.add("clarified");
    return variableNames;
  }

}
