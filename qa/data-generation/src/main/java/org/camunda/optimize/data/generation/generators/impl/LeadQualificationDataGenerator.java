package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LeadQualificationDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/lead-qualification.bpmn";

  public LeadQualificationDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("qualified", ThreadLocalRandom.current().nextDouble());
    variables.put("sdrAvailable", ThreadLocalRandom.current().nextDouble());
    variables.put("landingPage", ThreadLocalRandom.current().nextDouble());
    variables.put("crmLeadAppearance", ThreadLocalRandom.current().nextDouble());
    variables.put("reviewDcOutcome", ThreadLocalRandom.current().nextDouble());
    variables.put("basicQualificationResult", ThreadLocalRandom.current().nextDouble());
    variables.put("responseResult", ThreadLocalRandom.current().nextDouble());
    variables.put("dcOutcome", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
