/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeIdsToNamesRequestDto;
import org.camunda.optimize.dto.optimize.rest.FlowNodeNamesResponseDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;



public class FlowNodeMappingIT {
  private static final String A_START = "aStart";
  private static final String A_TASK = "aTask";
  private static final String AN_END = "anEnd";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private final static String PROCESS_DEFINITION_KEY = "aProcess";

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);


  @Test
  public void mapFlowNodeIdsToNames() {
    // given
    BpmnModelInstance modelInstance = getNamedBpmnModelInstance();
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    FlowNodeNamesResponseDto result =
            embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
            .execute(FlowNodeNamesResponseDto.class, 200);

    // then
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(3));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
    assertThat(result.getFlowNodeNames().values().contains(A_TASK), is(true));
    assertThat(result.getFlowNodeNames().values().contains(AN_END), is(true));
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
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    StartEvent start = modelInstance.getModelElementsByType(StartEvent.class).iterator().next();


    // when
    FlowNodeIdsToNamesRequestDto flowNodeIdsToNamesRequestDto = new FlowNodeIdsToNamesRequestDto();
    flowNodeIdsToNamesRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    flowNodeIdsToNamesRequestDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    List<String> ids = new ArrayList<>();
    ids.add(start.getId());
    flowNodeIdsToNamesRequestDto.setNodeIds(ids);

    FlowNodeNamesResponseDto result =
            embeddedOptimizeRule
                    .getRequestExecutor()
                    .buildGetFlowNodeNames(flowNodeIdsToNamesRequestDto)
                    .execute(FlowNodeNamesResponseDto.class, 200);

    // then
    assertThat(result, is(notNullValue()));
    assertThat(result.getFlowNodeNames(), is(notNullValue()));

    assertThat(result.getFlowNodeNames().values().size(), is(1));
    assertThat(result.getFlowNodeNames().values().contains(A_START), is(true));
  }
}
