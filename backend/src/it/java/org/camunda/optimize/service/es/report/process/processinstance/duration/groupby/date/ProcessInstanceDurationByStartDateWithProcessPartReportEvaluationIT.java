/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.date;

import com.fasterxml.jackson.core.type.TypeReference;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class ProcessInstanceDurationByStartDateWithProcessPartReportEvaluationIT {

  private static final String END_EVENT = "endEvent";
  private static final String START_EVENT = "startEvent";
  private static final String START_LOOP = "mergeExclusiveGateway";
  private static final String END_LOOP = "splittingGateway";
  private static final String TEST_ACTIVITY = "testActivity";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));

    assertThat(evaluationResponse.getResult().getProcessInstanceCount(), is(1L));
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void reportEvaluationById() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    OffsetDateTime endDate = activityStartDate.plusSeconds(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      activityStartDate
    );
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(processInstanceDto.getDefinitionId(), endDate);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    ProcessReportDataDto reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    String reportId = createAndStoreDefaultReportDefinition(reportDataDto);

    // when
    ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluationResponse =
      evaluateReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));

    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.START_DATE));
    assertThat(resultReportDataDto.getParameters().getProcessPart(), is(notNullValue()));
    assertThat(evaluationResponse.getResult().getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(
      resultMap.get(localDateTimeToString(startOfToday)),
      is(calculateExpectedValueGivenDurations(1000L, 2000L, 9000L))
    );
  }

  @Test
  public void multipleEventsInEachDateRange() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstStartDate, -1, procDefDto, Arrays.asList(2, 4, 12));


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstStartDate, ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.keySet(), hasItem(expectedStringToday));
    assertThat(resultMap.get(expectedStringToday), is(calculateExpectedValueGivenDurations(1000L, 2000L, 9000L)));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.keySet(), hasItem(expectedStringYesterday));
    assertThat(resultMap.get(expectedStringYesterday), is(calculateExpectedValueGivenDurations(2000L, 4000L, 12000L)));
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() throws SQLException {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    startThreeProcessInstances(procInstStartDate, -1, procDefDto, Arrays.asList(2, 4, 12));
    startThreeProcessInstances(procInstStartDate, -2, procDefDto, Arrays.asList(2, 4, 12));
    startThreeProcessInstances(procInstStartDate, -3, procDefDto, Arrays.asList(2, 4, 12));


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }

  @Test
  public void takeCorrectActivityOccurrences() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now().minusHours(1);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartLoopingProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeFirstActivityInstanceStartDate(START_LOOP, activityStartDate);
    engineDatabaseRule.changeFirstActivityInstanceEndDate(END_LOOP, activityStartDate.plusSeconds(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_LOOP)
      .setEndFlowNodeId(END_LOOP)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(calculateExpectedValueGivenDurations(2000L)));
  }

  @Test
  public void unknownStartReturnsEmptyResult() throws SQLException {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceEndDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().plusHours(1)
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId("foo")
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().isEmpty(), is(true));
  }

  @Test
  public void unknownEndReturnsEmptyResult() throws SQLException {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDateForProcessDefinition(
      processInstanceDto.getDefinitionId(),
      OffsetDateTime.now().minusHours(1)
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId("foo")
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

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
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().isEmpty(), is(true));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(
      resultMap.get(localDateTimeToString(startOfToday)),
      is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L))
    );
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartdate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();

    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(9));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartdate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartdate.plusSeconds(2));
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(
      resultMap.get(localDateTimeToString(startOfToday)),
      is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L))
    );
  }

  @Test
  public void filterInReportWorks() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setFilter(createVariableFilter("true"))
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    assertThat(resultMap.keySet(), hasItem(localDateTimeToString(startOfToday)));
    assertThat(resultMap.get(localDateTimeToString(startOfToday)), is(calculateExpectedValueGivenDurations(1000L)));

    // when
    reportData.setFilter(createVariableFilter("false"));
    result = evaluateReport(reportData).getResult();

    // then
    resultMap = result.getData();
    assertThat(resultMap.isEmpty(), is(true));
  }

  @Test
  public void processInstancesStartedAtSameIntervalAreGroupedTogether() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(1));
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(2));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto.getId(), activityStartDate.plusSeconds(9));
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(1));
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(2));
    ZonedDateTime startOfToday = truncateToStartOfUnit(procInstStartDate, ChronoUnit.DAYS);
    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.keySet(), hasItem(expectedStringToday));
    assertThat(resultMap.get(expectedStringToday), is(calculateExpectedValueGivenDurations(1000L, 9000L, 2000L)));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.keySet(), hasItem(expectedStringYesterday));
    assertThat(resultMap.get(expectedStringYesterday), is(calculateExpectedValueGivenDurations(1000L)));
  }

  @Test
  public void resultIsSortedInDescendingOrder() {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, 0L);
    processInstanceDto = engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, -2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -1L);


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect descending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -2L, 3L);

    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 1L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  private static Object[] aggregationTypes() {
    return AggregationType.values();
  }

  @Test
  @Parameters(method = "aggregationTypes")
  public void testCustomOrderOnResultValueIsApplied(final AggregationType aggregationType) {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 4L);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationType(aggregationType);
    final ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    final List<Long> bucketValues = resultMap.values().stream()
      .map(bucketResult -> bucketResult.getResultForGivenAggregationType(aggregationType))
      .collect(Collectors.toList());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() throws Exception {
    // given
    OffsetDateTime procInstStartDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(procInstStartDate, 0, procDefDto, Arrays.asList(1, 2, 9));
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, -2L);
    engineDatabaseRule.changeActivityInstanceStartDate(processInstanceDto3.getId(), activityStartDate);
    engineDatabaseRule.changeActivityInstanceEndDate(processInstanceDto3.getId(), activityStartDate.plusSeconds(2));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));

    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);

    String expectedStringToday = localDateTimeToString(startOfToday);
    assertThat(resultMap.keySet(), hasItem(expectedStringToday));
    assertThat(resultMap.get(expectedStringToday), is(calculateExpectedValueGivenDurations(1000L, 2000L, 9000L)));
    String expectedStringYesterday = localDateTimeToString(startOfToday.minusDays(1));
    assertThat(resultMap.keySet(), hasItem(expectedStringYesterday));
    assertThat(resultMap.get(expectedStringYesterday), is(calculateExpectedValueGivenDurations(0L)));
    String expectedStringDayBeforeYesterday = localDateTimeToString(startOfToday.minusDays(2));
    assertThat(resultMap.keySet(), hasItem(expectedStringDayBeforeYesterday));
    assertThat(resultMap.get(expectedStringDayBeforeYesterday), is(calculateExpectedValueGivenDurations(2000L)));
  }

  @Test
  public void groupedByHour() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(5);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.HOURS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.HOUR)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertDateResultMap(resultMap, 5, now, ChronoUnit.HOURS);
  }

  @Test
  public void groupedByDay() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.DAYS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto processInstanceEngineDto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.DAYS);
  }

  @Test
  public void groupedByWeek() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.WEEKS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.WEEK)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.WEEKS);
  }

  @Test
  public void groupedByMonth() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.MONTHS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.MONTH)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.MONTHS);
  }

  @Test
  public void groupedByYear() throws Exception {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = OffsetDateTime.now();
    updateProcessInstancesDates(processInstanceDtos, now, ChronoUnit.YEARS);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.YEAR)
      .setProcessDefinitionKey(dto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(dto.getProcessDefinitionVersion())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .build();

    ProcessDurationReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, AggregationResultDto> resultMap = result.getData();
    assertDateResultMap(resultMap, 8, now, ChronoUnit.YEARS);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(int number) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, number)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private void assertDateResultMap(Map<String, AggregationResultDto> resultMap, int size, OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultMap.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultMap.keySet(), hasItem(expectedDateString));
        assertThat(resultMap.get(expectedDateString), is(calculateExpectedValueGivenDurations(1000L)));
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
      engineDatabaseRule.changeActivityInstanceStartDate(id, now);
      engineDatabaseRule.changeActivityInstanceEndDate(id, now.plusSeconds(1));
    }
    engineDatabaseRule.updateProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseRule.updateProcessInstanceEndDates(idToNewEndDate);

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


  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleServiceTaskProcess();
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineRule.startProcessInstance(processDefinitionEngineDto.getId());
    processInstanceEngineDto.setProcessDefinitionKey(processDefinitionEngineDto.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinitionEngineDto.getVersion()));
    return processInstanceEngineDto;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
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
    return engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
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
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    String id = createNewProcessReport();

    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId(id);
    report.setLastModifier("something");
    report.setName("something");
    report.setCreated(OffsetDateTime.now());
    report.setLastModified(OffsetDateTime.now());
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewProcessReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluateReportById(String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>>() {});
      // @formatter:on
  }

  private ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessDurationReportMapResultDto>>() {});
      // @formatter:on
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift) {
    adjustProcessInstanceDates(processInstanceId, startDate, daysToShift, null);
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          Long durationInSec) {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    try {
      engineDatabaseRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
      if (durationInSec != null) {
        engineDatabaseRule.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
      }
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException("Failed adjusting process instance dates", e);
    }
  }

  private String localDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(time);
  }

  private void startThreeProcessInstances(OffsetDateTime procInstStartDate,
                                          int daysToShiftProcessInstance,
                                          ProcessDefinitionEngineDto procDefDto,
                                          List<Integer> activityDurationsInSec) throws
                                                                                SQLException {
    OffsetDateTime activityStartDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto.getId(), procInstStartDate, daysToShiftProcessInstance);

    ProcessInstanceEngineDto processInstanceDto2 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto2.getId(), procInstStartDate, daysToShiftProcessInstance);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(procDefDto.getId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), procInstStartDate, daysToShiftProcessInstance);

    Map<String, OffsetDateTime> activityStartDatesToUpdate = new HashMap<>();
    Map<String, OffsetDateTime> endDatesToUpdate = new HashMap<>();
    activityStartDatesToUpdate.put(processInstanceDto.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate);
    activityStartDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate);
    endDatesToUpdate.put(processInstanceDto.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(0)));
    endDatesToUpdate.put(processInstanceDto2.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(1)));
    endDatesToUpdate.put(processInstanceDto3.getId(), activityStartDate.plusSeconds(activityDurationsInSec.get(2)));

    engineDatabaseRule.updateActivityInstanceStartDates(activityStartDatesToUpdate);
    engineDatabaseRule.updateActivityInstanceEndDates(endDatesToUpdate);
  }

  private AggregationResultDto calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    return new AggregationResultDto(
      Math.round(statistics.getMin()),
      Math.round(statistics.getMax()),
      Math.round(statistics.getMean()),
      Math.round(statistics.getPercentile(50.0D))
    );
  }
}
