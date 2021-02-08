/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class FlowNodeRestServiceIT extends AbstractIT {

  @Test
  public void mapFlowNodeWithoutAuthentication() {
    // given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey("aKey");
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void getFlowNodesWithNullNullParameter() {
    // given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(null);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void getFlowNodesForSharedDefinition() {
    // given
    final String key = "aKey";
    final String version = "1";
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), null);

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);

    FlowNodeNamesResponseDto response = getFlowNodeNamesWithoutAuth(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(response.getFlowNodeNames()).hasSize(1);
  }

  @Test
  public void getFlowNodesForTenantSpecificDefinition() {
    // given
    final String key = "aKey";
    final String version = "1";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), tenantId1);
    createProcessDefinition(key, version, ImmutableMap.of("1", "1", "2", "2"), tenantId2);
    importAllEngineEntitiesFromScratch();

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);
    flowNodeIdsToNamesRequestDto.setTenantId(tenantId2);

    FlowNodeNamesResponseDto response = getFlowNodeNamesWithoutAuth(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(response.getFlowNodeNames()).hasSize(2);
  }

  @Test
  public void getFlowNodesForSharedDefinitionByTenantWithNoSpecificDefinition() {
    // given
    final String key = "aKey";
    final String version = "1";
    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), null);
    importAllEngineEntitiesFromScratch();

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);
    flowNodeIdsToNamesRequestDto.setTenantId(tenantId1);

    FlowNodeNamesResponseDto response = getFlowNodeNamesWithoutAuth(flowNodeIdsToNamesRequestDto);

    // then
    assertThat(response.getFlowNodeNames()).hasSize(1);
  }

  private void createProcessDefinition(final String processDefinitionKey, final String processDefinitionVersion) {
    createProcessDefinition(processDefinitionKey, processDefinitionVersion, null, null);
  }

  private void createProcessDefinition(final String processDefinitionKey,
                                       final String processDefinitionVersion,
                                       final Map<String, String> flowNodeNames,
                                       final String tenantId) {
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    ProcessDefinitionOptimizeDto expected = ProcessDefinitionOptimizeDto.builder()
      .id(expectedProcessDefinitionId)
      .key(processDefinitionKey)
      .version(processDefinitionVersion)
      .tenantId(tenantId)
      .engine("testEngine")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedProcessDefinitionId,
      expected
    );
    createProcessDefinitionXml(processDefinitionKey, processDefinitionVersion, flowNodeNames, tenantId);
  }

  private void createProcessDefinitionXml(final String processDefinitionKey,
                                          final String processDefinitionVersion,
                                          final Map<String, String> flowNodeNames,
                                          final String tenantId) {
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    ProcessDefinitionOptimizeDto expectedXml = ProcessDefinitionOptimizeDto.builder()
      .id(expectedProcessDefinitionId)
      .key(processDefinitionKey)
      .version(processDefinitionVersion)
      .tenantId(tenantId)
      .flowNodeNames(flowNodeNames)
      .bpmn20Xml("XML123")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      expectedProcessDefinitionId,
      expectedXml
    );
  }

  private FlowNodeNamesResponseDto getFlowNodeNamesWithoutAuth(FlowNodeIdsToNamesRequestDto requestDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetFlowNodeNames(requestDto)
      .withoutAuthentication()
      .execute(FlowNodeNamesResponseDto.class, Response.Status.OK.getStatusCode());
  }
}
