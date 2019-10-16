/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.date;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public abstract class AbstractProcessInstanceDurationByDateWithProcessPartReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";

  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    OffsetDateTime procInstReferenceDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstReferenceDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseExtensionRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), not(Optional.empty()));

    assertThat(evaluationResponse.getResult().getInstanceCount(), is(1L));
    final List<MapResultEntryDto<Long>> resultData = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1000L));
  }

  @Test
  public void reportEvaluationById() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseExtensionRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    ProcessReportDataDto reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    String reportId = createNewReport(reportDataDto);

    // when
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      evaluateMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(resultReportDataDto.getConfiguration().getProcessPart(), not(Optional.empty()));
    assertThat(evaluationResponse.getResult().getData(), is(notNullValue()));
    final List<MapResultEntryDto<Long>> resultData = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1000L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 2000L, 9000L)));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(getTestReportDataType())
      .build();

    final Map<AggregationType, ReportMapResultDto> results =
      evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, new Long[]{1000L, 2000L, 9000L});
  }

  @Test
  public void multipleEventsInEachDateRange() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstRefDate, -1, procDefDto, Arrays.asList(2, 4, 12));


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstRefDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(
      resultData.get(0).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 2000L, 9000L))
    );
    assertThat(resultData.get(1).getKey(), is(localDateTimeToString(startOfToday.minusDays(1))));
    assertThat(
      resultData.get(1).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(2000L, 4000L, 12000L))
    );
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() throws SQLException {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstRefDate, -1, procDefDto, Arrays.asList(2, 4, 12));
    startThreeProcessInstances(procInstRefDate, -2, procDefDto, Arrays.asList(2, 4, 12));
    startThreeProcessInstances(procInstRefDate, -3, procDefDto, Arrays.asList(2, 4, 12));


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }


  @Test
  public void takeCorrectActivityOccurrences() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeFirstActivityInstanceStartDate(START_LOOP, activityStartDate);
    engineDatabaseExtensionRule.changeFirstActivityInstanceEndDate(END_LOOP, activityStartDate.plusSeconds(2));
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_LOOP)
      .setEndFlowNodeId(END_LOOP)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(2000L)));
  }

  @Test
  public void unknownStartReturnsEmptyResult() throws SQLException {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId("foo")
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().isEmpty(), is(true));
  }

  @Test
  public void unknownEndReturnsEmptyResult() throws SQLException {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId("foo")
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().isEmpty(), is(true));
  }

  @Test
  public void noAvailableProcessInstancesReturnsEmptyResult() {
    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("fooProcDef")
      .setProcessDefinitionVersion("1")
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().isEmpty(), is(true));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartdate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(9));
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 9000L, 2000L)));
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
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.HOUR)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void filterInReportWorks() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setFilter(createVariableFilter("true"))
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto<Long>> resultData = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));

    // when
    reportData.setFilter(createVariableFilter("false"));
    result = evaluateMapReport(reportData).getResult();

    // then
    resultData = result.getData();
    assertThat(resultData.isEmpty(), is(true));
  }

  @Test
  public void processInstancesAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(1));
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -1L);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstRefDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 9000L, 2000L)));
    assertThat(resultData.get(1).getKey(), is(localDateTimeToString(startOfToday.minusDays(1))));
    assertThat(resultData.get(1).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void resultIsSortedInDescendingOrder() {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, 0L);
    processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, -2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -1L);


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect descending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), referenceDate, -2L, 3L);

    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
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
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), referenceDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), referenceDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), referenceDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), referenceDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), referenceDate, -2L, 4L);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
        .setDateInterval(GroupByDateUnit.DAY)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setStartFlowNodeId(START_EVENT)
        .setEndFlowNodeId(END_EVENT)
        .setReportDataType(getTestReportDataType())
        .build();
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto<Long>> resultData = result.getData();
      assertThat(resultData.size(), is(3));
      final List<Long> bucketValues = resultData.stream()
        .map(MapResultEntryDto::getValue)
        .collect(Collectors.toList());
      assertThat(
        bucketValues,
        contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
      );
    });
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    OffsetDateTime procInstRefDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstRefDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, -2L);
    engineDatabaseExtensionRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseExtensionRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(2));

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto<Long>> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 2000L, 9000L)));
    assertThat(resultData.get(1).getKey(), is(localDateTimeToString(startOfToday.minusDays(1))));
    assertThat(resultData.get(1).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(0L)));
    assertThat(resultData.get(2).getKey(), is(localDateTimeToString(startOfToday.minusDays(2))));
    assertThat(resultData.get(2).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(2000L)));
  }

  @Test
  public void groupedByHour() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.HOUR)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 5, now, ChronoUnit.HOURS);
  }

  @Test
  public void groupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 8, now, ChronoUnit.DAYS);
  }

  @Test
  public void groupedByWeek() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.WEEK)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 8, now, ChronoUnit.WEEKS);
  }

  @Test
  public void groupedByMonth() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.MONTH)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 8, now, ChronoUnit.MONTHS);
  }

  @Test
  public void groupedByYear() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.YEAR)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(getTestReportDataType())
      .build();

    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 8, now, ChronoUnit.YEARS);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private void assertDateResultMap(List<MapResultEntryDto<Long>> resultData,
                                   int size,
                                   OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultData.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultData.get(i).getKey(), is(expectedDateString));
        assertThat(resultData.get(i).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
      });
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) throws SQLException {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    for (int i = 0; i < procInsts.size(); i++) {
      String id = procInsts.get(i).getId();
      OffsetDateTime newStartDate = now.minus(i, unit);
      idToNewStartDate.put(id, newStartDate);
      idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      engineDatabaseExtensionRule.changeActivityInstanceStartDate(id, now);
      engineDatabaseExtensionRule.changeActivityInstanceEndDate(id, now.plusSeconds(1));
    }
    engineDatabaseExtensionRule.updateProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseExtensionRule.updateProcessInstanceEndDates(idToNewEndDate);

  }

  private List<ProcessFilterDto> createVariableFilter(String value) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .booleanType()
      .values(Collections.singletonList(value))
      .name("var")
      .add()
      .buildList();
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartLoopingProcess() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
    .startEvent("startEvent")
    .exclusiveGateway(START_LOOP)
      .serviceTask()
        .camundaExpression("${true}")
      .exclusiveGateway(END_LOOP)
        .condition("Take another round", "${!anotherRound}")
      .endEvent("endEvent")
    .moveToLastGateway()
      .condition("End process", "${anotherRound}")
      .serviceTask("serviceTask")
        .camundaExpression("${true}")
        .camundaInputParameter("anotherRound", "${anotherRound}")
        .camundaOutputParameter("anotherRound", "${!anotherRound}")
      .scriptTask("scriptTask")
        .scriptFormat("groovy")
        .scriptText("sleep(10)")
      .connectTo("mergeExclusiveGateway")
    .done();
    // @formatter:on
    Map<String, Object> variables = new HashMap<>();
    variables.put("anotherRound", true);
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(modelInstance, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(TEST_ACTIVITY)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime referenceDate,
                                            long daysToShift) {
    adjustProcessInstanceDates(processInstanceId, referenceDate, daysToShift, null);
  }

  protected abstract void adjustProcessInstanceDates(String processInstanceId,
                                                     OffsetDateTime referenceDate,
                                                     long daysToShift,
                                                     Long durationInSec);

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtensionRule.getDateTimeFormatter().format(time);
  }

  protected void startThreeProcessInstances(OffsetDateTime procInstRefDate,
                                            int daysToShiftProcessInstance,
                                            ProcessDefinitionEngineDto procDefDto,
                                            List<Integer> activityDurationsInSec) throws
                                                                                  SQLException {
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtensionRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstRefDate, daysToShiftProcessInstance);

    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtensionRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto2.getId(), procInstRefDate, daysToShiftProcessInstance);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtensionRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstRefDate, daysToShiftProcessInstance);

    Map<String, OffsetDateTime> activityStartDatesToUpdate = new HashMap<>();
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    activityStartDatesToUpdate.put(processInstanceDto.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate);
    endDatesToUpdate.put(processInstanceDto.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(0)));
    endDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(1)));
    endDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(2)));

    engineDatabaseExtensionRule.updateActivityInstanceStartDates(activityStartDatesToUpdate);
    engineDatabaseExtensionRule.updateActivityInstanceEndDates(endDatesToUpdate);
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      ReportMapResultDto result = evaluateMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private void assertDurationMapReportResults(Map<AggregationType, ReportMapResultDto> results,
                                              Long[] expectedDurations) {

    aggregationTypes.forEach((AggregationType aggType) -> {
      final List<MapResultEntryDto<Long>> resultData = results.get(aggType).getData();
      assertThat(resultData, is(notNullValue()));
      assertThat(
        resultData.get(0).getValue(),
        is(calculateExpectedValueGivenDurations(expectedDurations).get(aggType))
      );
    });
  }
}