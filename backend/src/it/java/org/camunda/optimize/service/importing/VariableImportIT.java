/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.importing.engine.service.VariableUpdateInstanceImportService;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class VariableImportIT extends AbstractImportIT {

  @RegisterExtension
  protected final LogCapturer variableImportServiceLogCapturer = LogCapturer.create()
    .forLevel(Level.INFO)
    .captureForType(VariableUpdateInstanceImportService.class);

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
    final String variableName = "var";
    final ComplexVariableDto complexVariableDto = createComplexVariableDto();
    final Map<String, Object> variables = new HashMap<>();
    variables.put(variableName, complexVariableDto);
    final ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskWithVariables(variables);
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).isEmpty();
    assertThat(storedVariableUpdateInstances).isEmpty();
    variableImportServiceLogCapturer.assertContains(String.format(
      "Refuse to add variable [%s] with type [%s] from variable import adapter plugin. " +
        "Variable has no type or type is not supported",
      variableName,
      complexVariableDto.getType()
    ));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void objectVariableValueIsFetchedDependingOnConfig(final boolean includeObjectVariableValue) {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .setEngineImportVariableIncludeObjectVariableValue(includeObjectVariableValue);

    final ComplexVariableDto complexVariableDto = createComplexVariableDto();
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var", complexVariableDto);
    deployAndStartSimpleServiceTaskWithVariables(variables);

    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    engineMockServer.verify(
      request()
        .withPath(engineIntegrationExtension.getEnginePath() + EngineConstants.VARIABLE_UPDATE_ENDPOINT)
        .withQueryStringParameter("occurredAfter", ".*")
        // our config has include semantics, api has exclude semantics, thus opposite is expected
        .withQueryStringParameter("excludeObjectValues", String.valueOf(!includeObjectVariableValue)),
      VerificationTimes.once()
    );
    engineMockServer.verify(
      request()
        .withPath(engineIntegrationExtension.getEnginePath() + EngineConstants.VARIABLE_UPDATE_ENDPOINT)
        .withQueryStringParameter("occurredAt", ".*")
        // our config has include semantics, api has exclude semantics, thus opposite is expected
        .withQueryStringParameter("excludeObjectValues", String.valueOf(!includeObjectVariableValue)),
      VerificationTimes.once()
    );
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

  @SuppressWarnings(UNCHECKED_CAST)
  @Test
  public void variablesCanHaveNullValue() throws JsonProcessingException {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariablesWithNullValues();
    engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
    importAllEngineEntitiesFromScratch();

    // when
    SearchResponse responseForAllDocumentsOfIndex = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    for (SearchHit searchHit : responseForAllDocumentsOfIndex.getHits()) {
      List<Map> retrievedVariables = (List<Map>) searchHit.getSourceAsMap().get(VARIABLES);
      assertThat(retrievedVariables).hasSize(variables.size());
      retrievedVariables.forEach(var -> assertThat(var.get(VARIABLE_VALUE)).isNull());
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

  @SneakyThrows
  @Test
  public void variablesWithoutDefinitionKeyCanBeImported() {
    // given
    final BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);

    engineDatabaseExtension.removeProcessDefinitionKeyFromAllHistoricVariableUpdates();

    // when
    importAllEngineEntitiesFromScratch();
    final List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariablesForProcessInstance(instanceDto);
    final List<VariableUpdateInstanceDto> storedVariableUpdateInstances = getStoredVariableUpdateInstances();

    // then
    assertThat(variablesResponseDtos).hasSize(variables.size());
    assertThat(storedVariableUpdateInstances).hasSize(variables.size());
  }

  private ComplexVariableDto createComplexVariableDto() {
    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType(EngineConstants.VARIABLE_TYPE_OBJECT);
    complexVariableDto.setValue(null);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("java.util.ArrayList");
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);
    complexVariableDto.setValueInfo(info);
    return complexVariableDto;
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
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    List<MapResultEntryDto> flowNodeIdToExecutionFrequency = result.getFirstMeasureData();
    for (MapResultEntryDto frequency : flowNodeIdToExecutionFrequency) {
      assertThat(frequency.getValue()).isEqualTo(4L);
    }
  }
}
