/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.retrieval;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static io.camunda.optimize.util.ZeebeBpmnModels.END_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import io.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class FlowNodeMappingIT extends AbstractCCSMIT {

  private static final String A_START = "aStart";
  private static final String A_TASK = "aTask";
  private static final String AN_END = "anEnd";

  private static final String PROCESS_DEFINITION_KEY = "aProcess";

  @Test
  public void mapFlowNodeIdsToNames() {
    // given
    final BpmnModelInstance modelInstance = getNamedBpmnModelInstance();

    final Process processDefinition = zeebeExtension.deployProcess(modelInstance);
    importAllZeebeEntitiesFromScratch();
    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto =
        new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(
        String.valueOf(processDefinition.getProcessDefinitionKey()));
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(
        String.valueOf(processDefinition.getVersion()));
    final FlowNodeNamesResponseDto result =
        flowNodeNamesClient.getFlowNodeNames(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getFlowNodeNames()).isNotNull();

    assertThat(result.getFlowNodeNames())
        .hasSize(3)
        .containsValues(START_EVENT, SERVICE_TASK, END_EVENT);
  }

  public static BpmnModelInstance getNamedBpmnModelInstance() {
    final String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    return Bpmn.createExecutableProcess(processId)
        .name("processName")
        .startEvent(START_EVENT)
        .name(START_EVENT)
        .serviceTask(SERVICE_TASK)
        .zeebeJobType(SERVICE_TASK)
        .name(SERVICE_TASK)
        .endEvent(END_EVENT)
        .name(null)
        .done();
    // @formatter:on
  }

  //  @Test
  //  public void mapFilteredFlowNodeIdsToNames() {
  //    // given
  //    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
  //    ProcessDefinitionEngineDto processDefinition =
  //        engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  //    importAllEngineEntitiesFromScratch();
  //    StartEvent start = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();
  //
  //    // when
  //    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new
  // FlowNodeIdsToNamesRequestDto();
  //    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
  //    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(
  //        String.valueOf(processDefinition.getVersion()));
  //    List<String> ids = new ArrayList<>();
  //    ids.add(start.getId());
  //    flowNodeIdsToNamesRequestDto.setNodeIds(ids);
  //
  //    FlowNodeNamesResponseDto result =
  //        flowNodeNamesClient.getFlowNodeNames(flowNodeIdsToNamesRequestDto);
  //
  //    // then
  //    assertThat(result).isNotNull();
  //    assertThat(result.getFlowNodeNames()).isNotNull();
  //
  //    assertThat(result.getFlowNodeNames()).hasSize(1).containsValue(A_START);
  //  }
}
