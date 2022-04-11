/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FlowNodeMappingIT extends AbstractIT {

  private static final String A_START = "aStart";
  private static final String A_TASK = "aTask";
  private static final String AN_END = "anEnd";

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Test
  public void mapFlowNodeIdsToNames() {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    FlowNodeNamesResponseDto result = flowNodeNamesClient.getFlowNodeNames(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getFlowNodeNames()).isNotNull();

    assertThat(result.getFlowNodeNames()).hasSize(3).containsValues(A_START, A_TASK, AN_END);
  }

  private BpmnModelInstance getNamedBpmnModelInstance() {
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .name(A_START)
          .serviceTask()
          .name(A_TASK)
          .camundaExpression("${true}")
        .endEvent()
          .name(AN_END)
        .done();
    // @formatter:on
  }

  @Test
  public void mapFilteredFlowNodeIdsToNames() {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      modelInstance);
    importAllEngineEntitiesFromScratch();
    StartEvent start = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();


    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    List<String> ids = new ArrayList<>();
    ids.add(start.getId());
    flowNodeIdsToNamesRequestDto.setNodeIds(ids);

    FlowNodeNamesResponseDto result = flowNodeNamesClient.getFlowNodeNames(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getFlowNodeNames()).isNotNull();

    assertThat(result.getFlowNodeNames()).hasSize(1).containsValue(A_START);
  }
}
