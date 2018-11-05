package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TransshipmentArrangementDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/transshipment-arrangement.bpmn";

  public TransshipmentArrangementDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("isTransferShipment", ThreadLocalRandom.current().nextDouble());
    variables.put("secondSectorBooked", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
