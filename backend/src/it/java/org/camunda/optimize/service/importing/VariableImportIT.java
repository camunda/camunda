/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class VariableImportIT extends AbstractImportIT {

  @Test
  public void deletionOfProcessInstancesDoesNotDistortVariableInstanceImport() {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    assertThatEveryFlowNodeWasExecuted4Times(firstProcInst.getProcessDefinitionKey());
  }

  @Test
  public void variableImportWorks() {
    //given
    BpmnModelInstance processModel = createSimpleProcessDefinition();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then
    assertThat(variablesResponseDtos.size(), is(variables.size()));
  }

  @Test
  public void variablesWithComplexTypeAreNotImported() {
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then
    assertThat(variablesResponseDtos.size(), is(0));
  }


  @Test
  public void variableUpdateImport() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then
    assertThat(variablesResponseDtos.size(), is(variables.size()));
  }

  @Test
  public void variablesCanHaveNullValue() throws IOException {
    //given
    BpmnModelInstance processModel = createSimpleProcessDefinition();

    Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariablesWithNullValues();
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    SearchResponse responseForAllDocumentsOfIndex =
      getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);

    // then
    for (SearchHit searchHit : responseForAllDocumentsOfIndex.getHits()) {
      List<Map> retrievedVariables = (List<Map>) searchHit.getSourceAsMap().get(VARIABLES);
      assertThat(retrievedVariables.size(), is(variables.size()));
      retrievedVariables.forEach(var -> assertThat(var.get("value"), nullValue()));
    }
  }

  @Test
  public void variableUpdatesOnSameVariableDoNotCreateSeveralVariables() {
    //given
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
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then
    assertThat(variablesResponseDtos.size(), is(1));
  }

  @Test
  public void onlyTheLatestVariableValueUpdateIsImported() {
    //given
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
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, 200);

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("bar"));
  }

  @Test
  public void variablesForCompletedProcessInstancesAreFinalResult() {
    //given
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

    ProcessInstanceEngineDto instanceDto = engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, 200);

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("bar"));

    // when
    requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("anotherVar");
    requestDto.setType(STRING);
    variableValues =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, 200);

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("2"));
  }

  @Test
  public void oldVariableUpdatesAreOverwritten() {
    //given
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
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    engineRule.finishAllRunningUserTasks(instanceDto.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, 200);

    //then
    assertThat(variableValues.size(), is(1));
    assertThat(variableValues.get(0), is("foo"));
  }

  @Test
  public void deletingARuntimeVariableAlsoRemovesItFromOptimize() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "aStringValue");
    ProcessInstanceEngineDto instanceDto =
      engineRule.deployAndStartProcessWithVariables(processModel, variables);
    engineRule.deleteVariableInstanceForProcessInstance("stringVar", instanceDto.getId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    requestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    requestDto.setName("stringVar");
    requestDto.setType(STRING);
    List<String> variableValues =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(requestDto)
        .executeAndReturnList(String.class, 200);

    //then
    assertThat(variableValues.size(), is(0));
  }

  private List<ProcessVariableNameResponseDto> getVariables(ProcessInstanceEngineDto instanceDto) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(instanceDto.getProcessDefinitionKey());
    variableRequestDto.setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion());
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildProcessVariableNamesRequest(variableRequestDto)
      .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    engineRule.deleteHistoricProcessInstance(firstProcInst.getId());
    engineRule.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private void assertThatEveryFlowNodeWasExecuted4Times(String processDefinitionKey) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    ProcessCountReportMapResultDto result = evaluateReport(reportData).getResult();
    assertThat(result.getData(), is(notNullValue()));
    List<MapResultEntryDto<Long>> flowNodeIdToExecutionFrequency = result.getData();
    for (MapResultEntryDto<Long> frequency : flowNodeIdToExecutionFrequency) {
      assertThat(frequency.getValue(), is(4L));
    }
  }

  private AuthorizedProcessReportEvaluationResultDto<ProcessCountReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {});
      // @formatter:on
  }

}
