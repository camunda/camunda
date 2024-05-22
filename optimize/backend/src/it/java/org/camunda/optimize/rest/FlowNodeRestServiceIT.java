/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

import com.google.common.collect.ImmutableMap;
import jakarta.ws.rs.core.Response;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class FlowNodeRestServiceIT extends AbstractPlatformIT {
  private static final String START = "aStart";
  private static final String END = "anEnd";

  @Test
  public void getFlowNodeNamesWithoutAuthentication() {
    // given
    final ProcessInstanceEngineDto processInstance =
        engineIntegrationExtension.deployAndStartProcess(
            getSimpleBpmnDiagram("aProcess", START, END));
    importAllEngineEntitiesFromScratch();

    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(
        processInstance.getProcessDefinitionVersion());

    Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildGetFlowNodeNamesExternal(flowNodeIdsToNamesRequestDto)
            .withoutAuthentication()
            .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void getFlowNodesForDefinition() {
    // given
    final ProcessInstanceEngineDto processInstance =
        engineIntegrationExtension.deployAndStartProcess(
            getSimpleBpmnDiagram("aProcess", START, END));
    importAllEngineEntitiesFromScratch();

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto =
        new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(
        processInstance.getProcessDefinitionVersion());

    FlowNodeNamesResponseDto response = getFlowNodeNamesWithoutAuth(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(response.getFlowNodeNames()).hasSize(2);
    assertThat(response.getFlowNodeNames())
        .containsExactlyEntriesOf(
            ImmutableMap.of(
                END, END,
                START, START));
  }

  @Test
  public void getFlowNodesWithNullParameter() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("aProcess1", START, END));

    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(null);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");
    FlowNodeNamesResponseDto response = getFlowNodeNamesWithoutAuth(flowNodeIdsToNamesRequestDto);

    assertThat(response.getFlowNodeNames()).isEmpty();
  }

  private FlowNodeNamesResponseDto getFlowNodeNamesWithoutAuth(
      FlowNodeIdsToNamesRequestDto requestDto) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetFlowNodeNamesExternal(requestDto)
        .withoutAuthentication()
        .execute(FlowNodeNamesResponseDto.class, Response.Status.OK.getStatusCode());
  }
}
