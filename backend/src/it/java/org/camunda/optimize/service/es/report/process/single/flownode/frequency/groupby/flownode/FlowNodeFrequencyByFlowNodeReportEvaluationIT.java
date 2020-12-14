/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.flownode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class FlowNodeFrequencyByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("2");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(4);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessInstanceEngineDto firstProcess = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("3");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(4);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("2");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    ProcessInstanceEngineDto firstProcess = deployProcessWithTwoTasks();
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("3");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        latestProcess.getProcessDefinitionKey(),
        ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
      );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void worksWithNullTenants() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(engineDto.getProcessDefinitionKey(), ALL_VERSIONS);
    reportData.setTenantIds(Collections.singletonList(null));
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
  }

  @Test
  public void orderOfTenantSelectionDoesNotAffectResult() {
    // given
    final String definitionKey = "aKey";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    final String otherTenantId = "tenant1";

    engineIntegrationExtension.createTenant(otherTenantId);

    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(definitionKey), noneTenantId);

    importAllEngineEntitiesFromScratch();

    List<String> tenantListNoneTenantFirst = Lists.newArrayList(noneTenantId, otherTenantId);
    List<String> tenantListOtherTenantFirst = Lists.newArrayList(otherTenantId, noneTenantId);

    // when
    final ReportMapResultDto resultNoneTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListNoneTenantFirst);
    final ReportMapResultDto resultOtherTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListOtherTenantFirst);

    // then
    assertThat(resultNoneTenantFirst.getData()).isNotEmpty();
    assertThat(resultOtherTenantFirst.getData()).isEqualTo(resultNoneTenantFirst.getData());
  }

  @SneakyThrows
  @Test
  public void reportEvaluationForSharedDefinitionAndInstancesOnSpecificTenants() {
    // given
    final String definitionKey = "aKey";
    final String tenantId1 = "tenantId1";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    engineIntegrationExtension.createTenant(tenantId1);

    // To create specific tenant instances with a shared def, start instance on noneTenant and update tenantID after
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension
      .deployAndStartProcess(getSimpleBpmnDiagram(definitionKey), noneTenantId);
    engineDatabaseExtension.changeProcessInstanceTenantId(instance1.getId(), tenantId1);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definitionKey, ALL_VERSIONS);
    reportData.setTenantIds(newArrayList(tenantId1));
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotEmpty();
    assertThat(result.getEntryForKey(START_EVENT).get().getValue()).isEqualTo(1);
    assertThat(result.getEntryForKey(END_EVENT).get().getValue()).isEqualTo(1);
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

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithExecutionStateRunning() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.RUNNING);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getEntryForKey("startEvent")).isPresent().get().extracting(MapResultEntryDto::getValue).isNull();
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithExecutionStateCompleted() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.COMPLETED);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getEntryForKey("startEvent")).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get().extracting(MapResultEntryDto::getValue).isNull();
  }

  @Test
  public void evaluateReportWithExecutionStateCanceled() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.cancelActivityInstance(processInstanceDto.getId(), USER_TASK_1);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.CANCELED);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getEntryForKey("startEvent")).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isNull();
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithExecutionStateAll() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setFlowNodeExecutionState(FlowNodeExecutionState.ALL);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then

    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getEntryForKey("startEvent").get().getValue()).isEqualTo(1.);
    assertThat(result.getEntryForKey(BpmnModels.USER_TASK_1).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(resultDto)).isEqualTo(1L);
    assertThat(resultDto.getIsComplete()).isFalse();
  }

  @Test
  public void evaluateReportForMultipleEventsWithMultipleProcesses() {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(instanceDto.getDefinitionId());

    ProcessInstanceEngineDto instanceDto2 = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse1 =
      reportClient.evaluateMapReport(
        reportData);
    reportData.setProcessDefinitionKey(instanceDto2.getProcessDefinitionKey());
    reportData.setProcessDefinitionVersion(instanceDto2.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse2 =
      reportClient.evaluateMapReport(
        reportData);

    // then
    final ProcessReportDataDto resultReportDataDto1 = evaluationResponse1.getReportDefinition().getData();
    assertThat(resultReportDataDto1.getProcessDefinitionKey()).isEqualTo(instanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto1.getDefinitionVersions()).contains(instanceDto.getProcessDefinitionVersion());
    final ReportMapResultDto result1 = evaluationResponse1.getResult();
    assertThat(result1.getData()).isNotNull();
    assertThat(result1.getData()).hasSize(3);
    assertThat(result1.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(2.);

    final ProcessReportDataDto resultReportDataDto2 = evaluationResponse2.getReportDefinition().getData();
    assertThat(resultReportDataDto2.getProcessDefinitionKey()).isEqualTo(instanceDto2.getProcessDefinitionKey());
    assertThat(resultReportDataDto2.getDefinitionVersions()).contains(instanceDto2.getProcessDefinitionVersion());
    final ReportMapResultDto result2 = evaluationResponse2.getResult();
    assertThat(result2.getData()).isNotNull();
    assertThat(result2.getData()).hasSize(3);
    assertThat(result2.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
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

    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      instanceDto.getProcessDefinitionKey(),
      instanceDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(13);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(13L);
    assertThat(result.getEntryForKey(TEST_ACTIVITY + 0).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasks();
    deployAndStartSimpleServiceTaskProcess();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(4);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(3L);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
    ;
  }

  @Test
  public void resultContainsNonExecutedFlowNodes() {
    // given
    ProcessInstanceEngineDto engineDto =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(3);
    MapResultEntryDto notExecutedFlowNode =
      result.getEntryForKey("endEvent").get();
    assertThat(notExecutedFlowNode.getValue()).isNull();
  }

  @Test
  public void importWithMi() {
    // given
    final String subProcessKey = "testProcess";
    final String testMIProcess = "testMIProcess";

    BpmnModelInstance subProcess = BpmnModels.getSingleServiceTaskProcess(subProcessKey);
    engineIntegrationExtension.deployProcessAndGetId(subProcess);

    BpmnModelInstance model = BpmnModels.getMultiInstanceProcess(testMIProcess, subProcessKey);
    engineIntegrationExtension.deployAndStartProcess(model);

    engineIntegrationExtension.waitForAllProcessesToFinish();
    importAllEngineEntitiesFromScratch();

    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
    assertThat(definitions).hasSize(2);

    // when
    ProcessReportDataDto reportData =
      createReport(testMIProcess, "1");
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(5);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(5L);
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

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
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(0L);

    // when
    reportData = createReport(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    ProcessInstanceEngineDto firstInstance = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityDuration(firstInstance.getId(), TEST_ACTIVITY, 5000);
    ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());
    engineDatabaseExtension.changeActivityDuration(secondInstance.getId(), TEST_ACTIVITY, 10000);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      firstInstance.getProcessDefinitionKey(),
      firstInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(TEST_ACTIVITY, filterData(DurationFilterUnit.SECONDS, 10L, LESS_THAN))
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey(TEST_ACTIVITY)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setEntity(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setProperty(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasks() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
      .serviceTask(FlowNodeFrequencyByFlowNodeReportEvaluationIT.TEST_ACTIVITY)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .camundaExpression("${true}")
      .endEvent("end")
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private long getExecutedFlowNodeCount(ReportMapResultDto resultList) {
    return resultList.getData().stream().filter(result -> result.getValue() != null).count();
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String definitionVersion) {
    return createReport(processDefinitionKey, ImmutableList.of(definitionVersion), Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String definitionVersion,
                                            List<String> tenantIds) {
    return createReport(processDefinitionKey, ImmutableList.of(definitionVersion), tenantIds);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, List<String> definitionVersions) {
    return createReport(processDefinitionKey, definitionVersions, Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, List<String> definitionVersions,
                                            List<String> tenantIds) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(definitionVersions)
      .setTenantIds(tenantIds)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
  }

  private ReportMapResultDto getReportEvaluationResult(final String definitionKey,
                                                       final String version,
                                                       final List<String> tenantIds) {
    ProcessReportDataDto reportData = createReport(
      definitionKey,
      version,
      tenantIds
    );
    return reportClient.evaluateMapReport(reportData).getResult();
  }
}
