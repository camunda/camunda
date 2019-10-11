/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.flownode.frequency;

import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.Is;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountFlowNodeFrequencyByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";


  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    //then
    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    ProcessInstanceEngineDto firstProcess = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("3"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse =
      evaluateMapReport(reportData);

    //then
    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(4));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    //then
    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    ProcessInstanceEngineDto firstProcess = deployProcessWithTwoTasks();
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("3"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(
        latestProcess.getProcessDefinitionKey(),
        ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
      );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    //then
    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));
  }

  @Test
  public void worksWithNullTenants() {
    //given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(engineDto.getProcessDefinitionKey(), ALL_VERSIONS);
    reportData.setTenantIds(Collections.singletonList(null));
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    //then
    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));

    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(1L));
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));

    final ReportMapResult result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(1L));
  }

  @Test
  public void evaluateReportWithExecutionStateRunning() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.RUNNING);
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getDataEntryForKey("startEvent").get().getValue(), is(nullValue()));
    assertThat(result.getDataEntryForKey("userTask").get().getValue(), is(1L));
  }

  @Test
  public void evaluateReportWithExecutionStateCompleted() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.COMPLETED);
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getDataEntryForKey("startEvent").get().getValue(), is(1L));
    assertThat(result.getDataEntryForKey("userTask").get().getValue(), is(nullValue()));
  }

  @Test
  public void evaluateReportWithExecutionStateAll() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.ALL);
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then

    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getDataEntryForKey("startEvent").get().getValue(), is(1L));
    assertThat(result.getDataEntryForKey("userTask").get().getValue(), is(1L));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(2L));
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResult resultDto = evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount(), is(2L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(3));
    assertThat(getExecutedFlowNodeCount(resultDto), is(1L));
    assertThat(resultDto.getIsComplete(), is(false));
  }

  @Test
  public void evaluateReportForMultipleEventsWithMultipleProcesses() {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(instanceDto.getDefinitionId());

    ProcessInstanceEngineDto instanceDto2 = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse1 = evaluateMapReport(
      reportData);
    reportData.setProcessDefinitionKey(instanceDto2.getProcessDefinitionKey());
    reportData.setProcessDefinitionVersion(instanceDto2.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResult> evaluationResponse2 = evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto1 = evaluationResponse1.getReportDefinition().getData();
    assertThat(resultReportDataDto1.getProcessDefinitionKey(), is(instanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto1.getDefinitionVersions(), contains(instanceDto.getProcessDefinitionVersion()));
    final ReportMapResult result1 = evaluationResponse1.getResult();
    assertThat(result1.getData(), is(notNullValue()));
    assertThat(result1.getData().size(), is(3));
    assertThat(result1.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(2L));

    final ProcessReportDataDto resultReportDataDto2 = evaluationResponse2.getReportDefinition().getData();
    assertThat(resultReportDataDto2.getProcessDefinitionKey(), is(instanceDto2.getProcessDefinitionKey()));
    assertThat(resultReportDataDto2.getDefinitionVersions(), contains(instanceDto2.getProcessDefinitionVersion()));
    final ReportMapResult result2 = evaluationResponse2.getResult();
    assertThat(result2.getData(), is(notNullValue()));
    assertThat(result2.getData().size(), is(3));
    assertThat(result2.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(1L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() {
    // given
    AbstractServiceTaskBuilder serviceTaskBuilder = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask(TEST_ACTIVITY + 0)
      .camundaExpression("${true}");
    for (int i = 1; i < 11; i++) {
      serviceTaskBuilder = serviceTaskBuilder
        .serviceTask(TEST_ACTIVITY + i)
        .camundaExpression("${true}");
    }
    BpmnModelInstance processModel =
      serviceTaskBuilder.endEvent()
        .done();

    ProcessInstanceEngineDto instanceDto = engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      instanceDto.getProcessDefinitionKey(),
      instanceDto.getProcessDefinitionVersion()
    );
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(13));
    assertThat(getExecutedFlowNodeCount(result), is(13L));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY + 0).get().getValue(), is(1L));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasks();
    deployAndStartSimpleServiceTaskProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(4));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    assertThat(getExecutedFlowNodeCount(result), is(3L));
    final List<Long> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(
      bucketValues,
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void resultContainsNonExecutedFlowNodes() {
    // given
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent("endEvent")
      .done();
    ProcessInstanceEngineDto engineDto = engineRule.deployAndStartProcess(subProcess);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(3));
    MapResultEntryDto<Long> notExecutedFlowNode =
      result.getDataEntryForKey("endEvent").get();
    assertThat(notExecutedFlowNode.getValue(), is(nullValue()));
  }

  @Test
  public void importWithMi() throws Exception {
    //given
    final String subProcessKey = "testProcess";
    final String callActivity = "callActivity";
    final String testMIProcess = "testMIProcess";

    // @formatter:off
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess(subProcessKey)
      .startEvent()
      .serviceTask("MI-Body-Task")
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
    engineRule.deployProcessAndGetId(subProcess);

    // @formatter:off
    BpmnModelInstance model = Bpmn.createExecutableProcess(testMIProcess)
      .name("MultiInstance")
      .startEvent("miStart")
      .parallelGateway()
        .endEvent("end1")
      .moveToLastGateway()
        .callActivity(callActivity)
        .calledElement(subProcessKey)
        .multiInstance()
        .cardinality("2")
        .multiInstanceDone()
        .endEvent("miEnd")
      .done();
    // @formatter:on
    engineRule.deployAndStartProcess(model);

    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
    assertThat(definitions.size(), is(2));

    //when
    ProcessReportDataDto reportData =
      createReport(testMIProcess, "1");
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    //then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(5));
    assertThat(getExecutedFlowNodeCount(result), is(5L));
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(null)
        .end(past.minusSeconds(1L))
        .add()
        .buildList());
    ReportMapResult result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(getExecutedFlowNodeCount(result), is(0L));

    // when
    reportData = createReport(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(3));
    assertThat(result.getDataEntryForKey(TEST_ACTIVITY).get().getValue(), is(1L));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasks() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
      .serviceTask(CountFlowNodeFrequencyByFlowNodeReportEvaluationIT.TEST_ACTIVITY)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .camundaExpression("${true}")
      .endEvent("end")
      .done();
    // @formatter:on
    return engineRule.deployAndStartProcess(modelInstance);
  }

  private long getExecutedFlowNodeCount(ReportMapResult resultList) {
    return resultList.getData().stream().filter(result -> result.getValue() != null).count();
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String definitionVersion) {
    return createReport(processDefinitionKey, ImmutableList.of(definitionVersion));
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, List<String> definitionVersions) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(definitionVersions)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
  }
}
