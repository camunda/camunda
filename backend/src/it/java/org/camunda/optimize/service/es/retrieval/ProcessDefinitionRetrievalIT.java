/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;


public class ProcessDefinitionRetrievalIT {

  private final static String PROCESS_DEFINITION_KEY = "aProcess";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
  }


  @Test
  public void getProcessDefinitionsWithMoreThenTen() {
    for (int i = 0; i < 11; i++) {
      // given
      deploySimpleServiceTaskProcessDefinition(PROCESS_DEFINITION_KEY + System.currentTimeMillis());
    }
    embeddedOptimizeRule.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(11);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    assertThat(definitions.size(), is(11));
  }

  @Test
  public void getProcessDefinitionsWithoutXml() {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
    assertThat(definitions.get(0).getBpmn20Xml(), nullValue());
  }

  @Test
  public void getProcessDefinitionsWithXml() {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
    assertThat(definitions.get(0).getBpmn20Xml(), is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void getProcessDefinitionsOnlyIncludeTheOnesWhereTheXmlIsImported() {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    addProcessDefinitionWithoutXmlToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndVersion() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(processDefinition.getKey(), processDefinition.getVersion())
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(Bpmn.convertToString(modelInstance)));
  }


  @Test
  public void getProcessDefinitionXmlByKeyAndAllVersion() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    engineRule.deployProcessAndGetProcessDefinition(modelInstance);
    modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
        .name("Add name to ensure that this is the latest version!")
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(processDefinition.getKey(), ALL_VERSIONS)
        .execute(String.class, 200);

    // then
    assertThat(actualXml, is(Bpmn.convertToString(modelInstance)));
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndAllVersionWithMoreThanTen() {

    final String definitionKey = PROCESS_DEFINITION_KEY + System.currentTimeMillis();

    // given 12 definitions (11 + 1 latest)
    for (int i = 0; i < 11; i++) {
      deploySimpleServiceTaskProcessDefinition(definitionKey);
    }

    BpmnModelInstance latestModelInstance = Bpmn.createExecutableProcess(definitionKey)
      .startEvent()
      .name("Add name to ensure that this is the latest version!")
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    engineRule.deployProcessAndGetProcessDefinition(latestModelInstance);

    embeddedOptimizeRule.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(12);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(definitionKey, ALL_VERSIONS)
        .execute(String.class, 200);

    // then: we get the latest version xml
    assertThat(actualXml, is(Bpmn.convertToString(latestModelInstance)));
  }

  private String deploySimpleServiceTaskProcessDefinition(String processId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    return engineRule.deployProcessAndGetId(modelInstance);
  }

  private void addProcessDefinitionWithoutXmlToElasticsearch() {
    ProcessDefinitionOptimizeDto processDefinitionWithoutXml = new ProcessDefinitionOptimizeDto()
      .setId("aProcDefId")
      .setKey("aProcDefKey")
      .setVersion("aProcDefVersion");
    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, "fooId", processDefinitionWithoutXml);
  }

}
