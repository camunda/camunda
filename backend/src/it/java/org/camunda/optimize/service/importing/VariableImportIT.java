/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class VariableImportIT extends AbstractImportIT {

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortVariableInstanceImport() {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // then
    assertThatEveryFlowNodeWasExecuted4Times(firstProcInst.getProcessDefinitionKey());
  }

  @Test
  public void variableImportWorks() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  @Test
  public void variableImportWorks_evenIfSeriesOfEsUpdateFailures() throws JsonProcessingException {
    // given
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getStoredVariableUpdateInstances()).isEmpty();

    // when
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );

    // whenES update writes fail
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest variableImportMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(variableImportMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importAllEngineEntitiesFromLastIndex();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then variables are stored as expected after ES writes successful
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
    esMockServer.verify(variableImportMatcher);
  }

  @Test
  public void variableImportExcludesVariableInstanceWritingIfFeatureDisabled() throws JsonProcessingException {
    // given
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);

    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).isEmpty();
  }

  @Test
  public void variablesWithComplexTypeAreNotImported() throws JsonProcessingException {
    // given
    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(null);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("java.util.ArrayList");
    info.setSerializationDataFormat("application/json");
    complexVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", complexVariableDto);
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).isEmpty();
    assertThat(storedVariableUpdateInstances).isEmpty();
  }

  @Test
  public void variableUpdateImport() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  @Test
  public void variablesCanHaveNullValue() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariablesWithNullValues();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    SearchResponse responseForAllDocumentsOfIndex = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    for (SearchHit searchHit : responseForAllDocumentsOfIndex.getHits()) {
      List<Map> retrievedVariables = (List<Map>) searchHit.getSourceAsMap().get(VARIABLES);
      assertThat(retrievedVariables).hasSize(variables.size());
      retrievedVariables.forEach(var -> assertThat(var.get("value")).isNull());
    }
    assertThat(storedVariableUpdateInstances)
      .hasSize(variables.size())
      .allSatisfy(instance -> assertThat(instance.getValue()).isNull());
  }

  @Test
  public void variableUpdatesOnSameVariableDoNotCreateSeveralVariables() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .userTask()
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);

    // then
    assertThat(variablesResponseDtos).hasSize(1);
  }

  @Test
  public void onlyTheLatestVariableValueUpdateIsImported() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("bar");
  }

  @Test
  public void variablesForCompletedProcessInstancesAreFinalResult() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "foo")
        .camundaOutputParameter("anotherVar", "1")
      .serviceTask()
        .camundaExpression("${true}")
        .camundaOutputParameter("stringVar", "bar")
        .camundaOutputParameter("anotherVar", "2")
      .endEvent()
      .done();
    // @formatter:on

    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("bar");

    // when
    requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("anotherVar");
    requestDto.setType(STRING);
    variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("2");
  }

  @Test
  public void oldVariableUpdatesAreOverwritten() {
    // given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .userTask()
        .serviceTask()
          .camundaExpression("${true}")
          .camundaOutputParameter("stringVar", "foo")
        .userTask()
      .endEvent()
      .done();
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    engineIntegrationExtension.finishAllRunningUserTasks(instanceDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);
    // then
    assertThat(variableValues)
      .hasSize(1)
      .first().isEqualTo("foo");
  }

  @Test
  public void deletingARuntimeVariableAlsoRemovesItFromOptimize() {
    // given
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    engineIntegrationExtension.deleteVariableInstanceForProcessInstance("stringVar", instanceDto.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues = variablesClient.getProcessVariableValues(requestDto);

    // then
    assertThat(variableValues).isEmpty();
  }

  private List<ProcessVariableNameResponseDto> getVariablesForProcessInstance(ProcessInstanceEngineDto instanceDto) {
    return variablesClient
      .getProcessVariableNames(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
  }

  private List<VariableUpdateInstanceDto> getStoredVariableUpdateInstances() throws JsonProcessingException {
    SearchResponse response = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = new ArrayList<>();
    for (SearchHit searchHitFields : response.getHits()) {
      final VariableUpdateInstanceDto variableUpdateInstanceDto = embeddedOptimizeExtension.getObjectMapper().readValue(
        searchHitFields.getSourceAsString(), VariableUpdateInstanceDto.class);
      storedVariableUpdateInstances.add(variableUpdateInstanceDto);
    }
    return storedVariableUpdateInstances;
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private void assertThatEveryFlowNodeWasExecuted4Times(String processDefinitionKey) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();
    assertThat(result.getData()).isNotNull();
    List<MapResultEntryDto> flowNodeIdToExecutionFrequency = result.getData();
    for (MapResultEntryDto frequency : flowNodeIdToExecutionFrequency) {
      assertThat(frequency.getValue()).isEqualTo(4L);
    }
  }
}
