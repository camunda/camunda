package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BookRequestDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/book-request.bpmn";

  public BookRequestDataGenerator(SimpleEngineClient engineClient) {
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
    return new HashSet<>();
  }

  @Override
  protected String[] getCorrelationNames() {
    return new String[]{"ReceivedBookRequest", "HoldBook", "DeclineHold"};
  }
}
