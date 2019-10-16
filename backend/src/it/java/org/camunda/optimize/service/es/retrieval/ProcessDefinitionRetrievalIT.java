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
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessDefinitionRetrievalIT {

  private final static String PROCESS_DEFINITION_KEY = "aProcess";
  private final static String VERSION_TAG = "aVersionTag";

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule);

  @Before
  public void setUp() {
  }

  @Test
  public void getProcessDefinitionsWithMoreThenTen() {
    for (int i = 0; i < 11; i++) {
      // given
      deploySimpleServiceTaskProcessDefinition(PROCESS_DEFINITION_KEY + System.currentTimeMillis());
    }
    embeddedOptimizeExtensionRule.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(11);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtensionRule
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getId(), is(processDefinitionId));
    assertThat(definitions.get(0).getKey(), is(processId));
    assertThat(definitions.get(0).getBpmn20Xml(), nullValue());
    assertThat(definitions.get(0).getVersion(), is("1"));
    assertThat(definitions.get(0).getVersionTag(), is(VERSION_TAG));
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
    String processDefinitionId = engineIntegrationExtensionRule.deployProcessAndGetId(modelInstance);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtensionRule
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    addProcessDefinitionWithoutXmlToElasticsearch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtensionRule
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
    engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtensionRule
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
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(
      modelInstance);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeExtensionRule
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
    engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(modelInstance);
    modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
        .name("Add name to ensure that this is the latest version!")
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(
      modelInstance);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeExtensionRule
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
    engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(latestModelInstance);

    embeddedOptimizeExtensionRule.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(12);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    String actualXml =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(definitionKey, ALL_VERSIONS)
        .execute(String.class, 200);

    // then: we get the latest version xml
    assertThat(actualXml, is(Bpmn.convertToString(latestModelInstance)));
  }

  private String deploySimpleServiceTaskProcessDefinition(String processId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processId)
      .camundaVersionTag(VERSION_TAG)
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployProcessAndGetId(modelInstance);
  }

  private void addProcessDefinitionWithoutXmlToElasticsearch() {
    ProcessDefinitionOptimizeDto processDefinitionWithoutXml = new ProcessDefinitionOptimizeDto()
      .setId("aProcDefId")
      .setKey("aProcDefKey")
      .setVersion("aProcDefVersion");
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      "fooId",
      processDefinitionWithoutXml
    );
  }

}
