/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.none;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class ProcessInstanceDurationByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  public static final String PROCESS_DEFINITION_KEY = "123";
  private static final String TEST_ACTIVITY = "testActivity";

  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());

    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), is(Optional.empty()));

    assertThat(evaluationResponse.getResult().getInstanceCount(), is(1L));
    long calculatedResult = evaluationResponse.getResult().getData();
    assertThat(calculatedResult, is(1000L));
  }

  @Test
  public void reportEvaluationById() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    ProcessReportDataDto reportDataDto =
      createReport(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());

    String reportId = createNewReport(reportDataDto);

    // when
    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      evaluateNumberReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.NONE));

    long calculatedResult = evaluationResponse.getResult().getData();
    assertThat(calculatedResult, is(1000L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseExtensionRule.updateProcessInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseExtensionRule.updateProcessInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportDataDto =
      createReport(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());
    NumberResultDto resultDto = evaluateNumberReport(reportDataDto).getResult();

    // then
    assertThat(resultDto.getData(), is(notNullValue()));
    Long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(notNullValue()));
    assertThat(calculatedResult, is(4000L));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    Map<String, OffsetDateTime> startDatesToUpdate = new HashMap<>();
    startDatesToUpdate.put(processInstanceDto.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto2.getId(), startDate);
    startDatesToUpdate.put(processInstanceDto3.getId(), startDate);
    engineDatabaseExtensionRule.updateProcessInstanceStartDates(startDatesToUpdate);
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    endDatesToUpdate.put(processInstanceDto.getId(), startDate.plusSeconds(1));
    endDatesToUpdate.put(processInstanceDto2.getId(), startDate.plusSeconds(2));
    endDatesToUpdate.put(processInstanceDto3.getId(), startDate.plusSeconds(9));
    engineDatabaseExtensionRule.updateProcessInstanceEndDates(endDatesToUpdate);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportDataDto =
      createReport(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());

    final Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results =
      evaluateMapReportForAllAggTypes(reportDataDto);

    // then
    assertAggregationResults(results);
  }


  @Test
  public void noAvailableProcessInstancesReturnsNull() {
    // when
    ProcessReportDataDto reportData = createReport("fooProcDef", "1");

    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getData(), is(nullValue()));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(9));
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportDataDto = createReport(processDefinitionKey, processDefinitionVersion);

    final Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results =
      evaluateMapReportForAllAggTypes(reportDataDto);

    // then
    assertAggregationResults(results);
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

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void filterInReportWorks() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceDto.getId(), startDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createReport(processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion());

    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .variable()
                           .booleanTrue()
                           .name("var")
                           .add()
                           .buildList());
    NumberResultDto resultDto = evaluateNumberReport(reportData).getResult();

    // then
    Long calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(1000L));

    // when
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .variable()
                           .booleanFalse()
                           .name("var")
                           .add()
                           .buildList());
    resultDto = evaluateNumberReport(reportData).getResult();

    // then
    calculatedResult = resultDto.getData();
    assertThat(calculatedResult, is(nullValue()));
  }


  @Test
  public void calculateDurationForRunningProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(completeProcessInstanceDto.getId(), completedProcInstStartDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtensionRule.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId()
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .runningInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setFilter(testExecutionStateFilter)
      .build();
    final NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    final Long resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData, is(runningProcInstStartDate.until(now, MILLIS)));
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(completeProcessInstanceDto.getId(), completedProcInstStartDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtensionRule.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId()
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .completedInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setFilter(testExecutionStateFilter)
      .build();
    final NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    final Long resultData = result.getData();
    assertThat(resultData, is(1000L));
  }


  @Test
  public void calculateDurationForRunningAndCompletedProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(completeProcessInstanceDto.getId(), completedProcInstStartDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtensionRule.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId()
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .build();

    final NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    final Long resultData = result.getData();
    assertThat(
      resultData,
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, runningProcInstStartDate.until(now, MILLIS)))
    );
  }

  @Test
  public void durationFilterWorksForRunningProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);


    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtensionRule.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(completeProcessInstanceDto.getId(), completedProcInstStartDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtensionRule.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId()
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(RelativeDateFilterUnit.HOURS.getId())
      .value(1L)
      .add()
      .buildList();

    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setFilter(testExecutionStateFilter)
      .build();
    final NumberResultDto result = evaluateNumberReport(reportData).getResult();

    // then
    final Long resultData = result.getData();
    assertThat(resultData, is(runningProcInstStartDate.until(now, MILLIS)));
  }


  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");

    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");

    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(ProcessInstanceDurationByNoneReportEvaluationIT.TEST_ACTIVITY)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> resultsMap =
      new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
        evaluateNumberReport(reportData);
      resultsMap.put(aggType, evaluationResponse);
    });
    return resultsMap;
  }

  private void assertAggregationResults(
    Map<AggregationType, AuthorizedProcessReportEvaluationResultDto<NumberResultDto>> results) {
    assertThat(results.get(AVERAGE).getResult().getData(), is(notNullValue()));
    assertThat(results.get(AVERAGE).getResult().getData(), is(4000L));
    assertThat(results.get(MIN).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MIN).getResult().getData(), is(1000L));
    assertThat(results.get(MAX).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MAX).getResult().getData(), is(9000L));
    assertThat(results.get(MEDIAN).getResult().getData(), is(notNullValue()));
    assertThat(results.get(MEDIAN).getResult().getData(), is(2000L));
  }

  private ProcessReportDataDto createReport(String processKey, String definitionVersion) {
    return ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_NONE)
      .build();
  }
}
