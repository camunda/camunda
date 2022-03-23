/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class ProcessDefinitionRetrievalIT extends AbstractIT {

  private final static String PROCESS_DEFINITION_KEY = "aProcess";
  private final static String VERSION_TAG = "aVersionTag";

  @Test
  public void getProcessDefinitionsWithMoreThanTen() {
    for (int i = 0; i < 11; i++) {
      // given
      deploySimpleServiceTaskProcessDefinition(PROCESS_DEFINITION_KEY + System.currentTimeMillis());
    }
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(11);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitions();

    assertThat(definitions).hasSize(11);
  }

  @Test
  public void getProcessDefinitionsWithoutXml() {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionId);
    assertThat(definitions.get(0).getKey()).isEqualTo(processId);
    assertThat(definitions.get(0).getBpmn20Xml()).isNull();
    assertThat(definitions.get(0).getVersion()).isEqualTo("1");
    assertThat(definitions.get(0).getVersionTag()).isEqualTo(VERSION_TAG);
  }

  @Test
  public void getProcessDefinitionsWithXml() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    BpmnModelInstance modelInstance = BpmnModels.getSingleServiceTaskProcess(processId);
    String processDefinitionId = engineIntegrationExtension.deployProcessAndGetId(modelInstance);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", true)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionId);
    assertThat(definitions.get(0).getKey()).isEqualTo(processId);
    assertThat(definitions.get(0).getBpmn20Xml()).isEqualTo(Bpmn.convertToString(modelInstance));
  }

  @Test
  public void getProcessDefinitionsOnlyIncludeTheOnesWhereTheXmlIsImported() {

    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    importAllEngineEntitiesFromScratch();
    addProcessDefinitionWithoutXmlToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessDefinitionOptimizeDto> definitions =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .addSingleQueryParam("includeXml", false)
        .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionId);
  }

  @Test
  public void getProcessDefinitionsWithSeveralEventsForSameDefinitionDeployed() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    String processDefinitionId = deploySimpleServiceTaskProcessDefinition(processId);
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = definitionClient.getAllProcessDefinitions();

    // then
    assertThat(definitions).hasSize(1);
    assertThat(definitions.get(0).getId()).isEqualTo(processDefinitionId);
    assertThat(definitions.get(0).getKey()).isEqualTo(processId);
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndVersion() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    BpmnModelInstance modelInstance = BpmnModels.getSingleServiceTaskProcess(processId);
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(
      processDefinition.getKey(),
      processDefinition.getVersionAsString(),
      null
    );

    // then
    assertThat(actualXml).isEqualTo(Bpmn.convertToString(modelInstance));
  }

  @Test
  public void getProcessDefinitionXmlByKeyAndAllVersion() {
    // given
    String processId = PROCESS_DEFINITION_KEY + System.currentTimeMillis();
    // @formatter:off
    BpmnModelInstance modelInstance = BpmnModels.getSingleServiceTaskProcess(processId);
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
    modelInstance = Bpmn.createExecutableProcess(processId)
      .startEvent()
        .name("Add name to ensure that this is the latest version!")
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);

    importAllEngineEntitiesFromScratch();

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(processDefinition.getKey(), ALL_VERSIONS, null);

    // then
    assertThat(actualXml).isEqualTo(Bpmn.convertToString(modelInstance));
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
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(latestModelInstance);

    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessDefinitionXmlMaxPageSize(12);
    importAllEngineEntitiesFromScratch();

    // when
    String actualXml = definitionClient.getProcessDefinitionXml(definitionKey, ALL_VERSIONS, null);

    // then: we get the latest version xml
    assertThat(actualXml).isEqualTo(Bpmn.convertToString(latestModelInstance));
  }

  private String deploySimpleServiceTaskProcessDefinition(String processId) {
    return engineIntegrationExtension.deployProcessAndGetId(BpmnModels.getSingleServiceTaskProcess(processId));
  }

  private void addProcessDefinitionWithoutXmlToElasticsearch() {
    ProcessDefinitionOptimizeDto processDefinitionWithoutXml = ProcessDefinitionOptimizeDto.builder()
      .id("aProcDefId")
      .key("aProcDefKey")
      .version("aProcDefVersion")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      "fooId",
      processDefinitionWithoutXml
    );
  }

}
