/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class FlowNodeRestServiceIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void mapFlowNodeWithoutAuthentication() {
    //given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey("aKey");
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute();

    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void getFlowNodesWithNullNullParameter() {
    //given
    createProcessDefinition("aKey", "1");
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(null);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion("1");

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute();

    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void getFlowNodesForSharedDefinition() {
    //given
    final String key = "aKey";
    final String version = "1";
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), null);

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);

    FlowNodeNamesResponseDto response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute(FlowNodeNamesResponseDto.class, 200);

    // then
    assertThat(response.getFlowNodeNames().size(), is(1));
  }

  @Test
  public void getFlowNodesForTenantSpecificDefinition() {
    //given
    final String key = "aKey";
    final String version = "1";
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), tenantId1);
    createProcessDefinition(key, version, ImmutableMap.of("1", "1", "2", "2"), tenantId2);

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);
    flowNodeIdsToNamesRequestDto.setTenantId(tenantId2);

    FlowNodeNamesResponseDto response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute(FlowNodeNamesResponseDto.class, 200);

    // then
    assertThat(response.getFlowNodeNames().size(), is(2));
  }

  @Test
  public void getFlowNodesForSharedDefinitionByTenantWithNoSpecificDefinition() {
    //given
    final String key = "aKey";
    final String version = "1";
    final String tenantId1 = "tenant1";
    createProcessDefinition(key, version, ImmutableMap.of("1", "1"), null);

    // when
    final FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(key);
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(version);
    flowNodeIdsToNamesRequestDto.setTenantId(tenantId1);

    FlowNodeNamesResponseDto response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
      .withoutAuthentication()
      .execute(FlowNodeNamesResponseDto.class, 200);

    // then
    assertThat(response.getFlowNodeNames().size(), is(1));
  }

  private void createProcessDefinition(final String processDefinitionKey, final String processDefinitionVersion) {
    createProcessDefinition(processDefinitionKey, processDefinitionVersion, null, null);
  }

  private void createProcessDefinition(final String processDefinitionKey,
                                       final String processDefinitionVersion,
                                       final Map<String, String> flowNodeNames,
                                       final String tenantId) {
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    ProcessDefinitionOptimizeDto expected = new ProcessDefinitionOptimizeDto()
      .setId(expectedProcessDefinitionId)
      .setKey(processDefinitionKey)
      .setVersion(processDefinitionVersion)
      .setTenantId(tenantId)
      .setEngine("testEngine");
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, expectedProcessDefinitionId, expected);
    createProcessDefinitionXml(processDefinitionKey, processDefinitionVersion, flowNodeNames, tenantId);
  }

  private void createProcessDefinitionXml(final String processDefinitionKey,
                                          final String processDefinitionVersion,
                                          final Map<String, String> flowNodeNames,
                                          final String tenantId) {
    String expectedProcessDefinitionId = processDefinitionKey + ":" + processDefinitionVersion;
    ProcessDefinitionOptimizeDto expectedXml = new ProcessDefinitionOptimizeDto()
      .setId(expectedProcessDefinitionId)
      .setKey(processDefinitionKey)
      .setVersion(processDefinitionVersion)
      .setTenantId(tenantId)
      .setFlowNodeNames(flowNodeNames)
      .setBpmn20Xml("XML123");
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, expectedProcessDefinitionId, expectedXml);
  }
}
