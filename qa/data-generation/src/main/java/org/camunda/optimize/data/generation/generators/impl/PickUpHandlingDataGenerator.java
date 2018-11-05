package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class PickUpHandlingDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/pick-up-handling.bpmn";

  public PickUpHandlingDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("changed", ThreadLocalRandom.current().nextDouble());
    variables.put("status", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
