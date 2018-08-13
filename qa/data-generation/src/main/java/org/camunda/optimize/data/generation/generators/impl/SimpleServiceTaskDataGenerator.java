package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashSet;
import java.util.Set;

public class SimpleServiceTaskDataGenerator extends DataGenerator {

  public SimpleServiceTaskDataGenerator(SimpleEngineClient simpleEngineClient) {
    super(simpleEngineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return Bpmn.createExecutableProcess("simpleServiceTask")
        .name("Simple Service Task Process")
          .startEvent()
            .serviceTask()
              .camundaExpression("${true}")
          .endEvent()
        .done();
  }

  public Set<String> getPathVariableNames() {
    return new HashSet<>();
  }

}
