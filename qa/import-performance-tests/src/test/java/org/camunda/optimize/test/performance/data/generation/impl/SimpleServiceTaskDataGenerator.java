package org.camunda.optimize.test.performance.data.generation.impl;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.performance.data.generation.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SimpleServiceTaskDataGenerator extends DataGenerator {

  private String processDefinitionId;

  public SimpleServiceTaskDataGenerator(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  protected BpmnModelInstance retrieveDiagram() {
    return Bpmn.createExecutableProcess(processDefinitionId)
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
