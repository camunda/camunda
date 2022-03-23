/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.processdefinition;

import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.importing.AbstractImportIT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessDefinitionImportIT extends AbstractImportIT {
  private static final String START = "aStart";
  private static final String END = "anEnd";

  @Test
  public void getProcessDefinitionFields() {
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(
      "aProcess",
      START,
      END
    ));

    // when
    importAllEngineEntitiesFromScratch();
    List<ProcessDefinitionOptimizeDto> processDefinitions = elasticSearchIntegrationTestExtension.getAllProcessDefinitions();

    // then
    assertThat(processDefinitions)
      .singleElement()
      .satisfies(definition -> assertThat(definition.getFlowNodeData())
        .containsExactly(
          new FlowNodeDataDto(END, END, END_EVENT),
          new FlowNodeDataDto(START, START, START_EVENT)
        ));
  }
}
