/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.SPLITTING_GATEWAY_ID;

public class TimeZoneAdjustmentRestServiceIT extends AbstractProcessDefinitionIT {

  @Test
  public void unknownTimezoneUsesServerTimezone() {
    // given
    OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final String collectionId = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "unknownTimezone")
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getCreated()).isEqualTo(now);
    assertThat(collection.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(collection.getCreated(), now)).isZero();
    assertThat(getOffsetDiffInHours(collection.getLastModified(), now)).isZero();
  }

  @Test
  public void omittedTimezoneUsesServerTimezone() {
    // given
    OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final String collectionId = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getCreated()).isEqualTo(now);
    assertThat(collection.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(collection.getCreated(), now)).isZero();
    assertThat(getOffsetDiffInHours(collection.getLastModified(), now)).isZero();
  }

  @ParameterizedTest
  @MethodSource("allProcessDateReports")
  public void adjustReportEvaluationResultToTimezone_processDateReports(final ProcessReportDataType reportType) {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setDistributeByDateInterval(AggregateByDateUnit.HOUR)
      .setProcessDefinitionKey(processInstanceDto1.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(reportType)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("someVariable")
      .setVariableType(VariableType.STRING)
      .build();

    // when
    final List<String> dateAsStringDateResultEntries = evaluateReportInLondonTimezoneAndReturnDateEntries(reportData);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @ParameterizedTest
  @MethodSource("allProcessDateReports")
  public void adjustReportEvaluationResultToTimezone_daylightSavingHoursAreRespected(final ProcessReportDataType reportType) {
    // given now is in summer time
    OffsetDateTime now = dateFreezer().dateToFreeze(OffsetDateTime.of(2020, 7, 5, 12, 0, 0, 0, ZoneOffset.UTC))
      .timezone("Europe/Berlin")
      .freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      // truncation of the date is in winter time
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .setDistributeByDateInterval(AggregateByDateUnit.YEAR)
      .setProcessDefinitionKey(processInstanceDto1.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(reportType)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("someVariable")
      .setVariableType(VariableType.STRING)
      .build();

    // when
    final List<String> dateAsStringDateResultEntries = evaluateReportInLondonTimezoneAndReturnDateEntries(reportData);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(date).isEqualTo(truncateToYearWithLondonTimezone(now)));
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_checkCorrectTruncation() {
    // for details see https://github.com/camunda/camunda-optimize/pull/2318#discussion_r451470038
    // given the truncation falls into the start of the year
    OffsetDateTime now = dateFreezer().dateToFreeze(OffsetDateTime.of(2020, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC))
      .freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), now);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // the timezone has an offset of -1(UTC)/-2 (UTC DST) and if the truncation is wrong
      // the result date would fall into the year 2019
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Atlantic/Cape_Verde")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      // @formatter:on
      .getResult();

    // then
    OffsetDateTime expectedDate =
      ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Atlantic/Cape_Verde")).toOffsetDateTime();
    assertThat(result.getData())
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(date).isEqualTo(expectedDate));
  }

  @ParameterizedTest
  @MethodSource("allProcessDateReports")
  public void adjustReportEvaluationResultToTimezone_processDateReports_automaticInterval(final ProcessReportDataType reportType) {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto1.getId(), START_EVENT, now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto2.getId(), START_EVENT, now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto1.getId(), START_EVENT, now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto2.getId(), START_EVENT, now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeUserTaskEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeActivityInstanceStartDate(processInstanceDto1.getId(), END_EVENT, now);
    engineDatabaseExtension.changeActivityInstanceEndDate(processInstanceDto1.getId(), END_EVENT, now);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setDistributeByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setProcessDefinitionKey(processInstanceDto1.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(reportType)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("someVariable")
      .setVariableType(VariableType.STRING)
      .build();

    // when
    final List<String> dateAsStringDateResultEntries = evaluateReportInLondonTimezoneAndReturnDateEntries(reportData);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void unsavedReportEvaluationDoesNotFailWithZOffsetLastModifiedDateFormat() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();
    final SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(reportData);
    reportDef.setLastModified(OffsetDateTime.parse("2021-01-05T10:25:16.161Z"));

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDef)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private static Stream<ProcessReportDataType> allProcessDateReports() {
    return ProcessReportDataType.allDateReports().stream();
  }

  @ParameterizedTest
  @MethodSource("allProcessVariableReports")
  public void adjustReportEvaluationResultToTimezone_groupOrDistributedByDateVariable(final ProcessReportDataType reportType) {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    Map<String, Object> variables = ImmutableMap.of("dateVar", now);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleProcessWithVariables(variables);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateVariableUnit(AggregateByDateUnit.HOUR)
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setDistributeByDateInterval(AggregateByDateUnit.HOUR)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(reportType)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    // when
    final List<String> dateAsStringDateResultEntries = evaluateReportInLondonTimezoneAndReturnDateEntries(reportData);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  private static Stream<ProcessReportDataType> allProcessVariableReports() {
    return ProcessReportDataType.allVariableReports().stream();
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_groupByDateVariable_automaticInterval() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    Map<String, Object> variables = ImmutableMap.of("dateVar", now);
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleProcessWithVariables(variables);
    variables = ImmutableMap.of("dateVar", now.plusDays(1));
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    // when
    final List<String> dateAsStringDateResultEntries = evaluateReportInLondonTimezoneAndReturnDateEntries(reportData);

    // then
    String expectedDateAsString = embeddedOptimizeExtension.getDateTimeFormatter()
      .format(now.atZoneSameInstant(ZoneId.of("Europe/London")).toOffsetDateTime());
    assertThat(dateAsStringDateResultEntries)
      .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    final String firstElement = dateAsStringDateResultEntries.get(0);
    assertThat(firstElement).isEqualTo(expectedDateAsString);

    expectedDateAsString = expectedLastDateAsStringForAutomaticInterval(now);
    final String lastElement = dateAsStringDateResultEntries.get(dateAsStringDateResultEntries.size() - 1);
    assertThat(lastElement).isEqualTo(expectedDateAsString);
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_decisionReport() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    final DecisionDefinitionEngineDto decisionDefinition =
      engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinition.getId());
    importAllEngineEntitiesFromScratch();

    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDateInterval(AggregateByDateUnit.HOUR)
      .setDecisionDefinitionKey(decisionDefinition.getKey())
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .build();

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto>>() {})
      .getResult();
      // @formatter:on

    // then
    assertThat(result.getData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_combinedDateReport() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId1 = reportClient.createSingleProcessReport(groupByDate);
    final String singleProcessReportId2 = reportClient.createSingleProcessReport(groupByDate);
    final String combinedReportId =
      reportClient.createNewCombinedReport(singleProcessReportId1, singleProcessReportId2);

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(combinedReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
    final CombinedProcessReportResultDataDto<ReportMapResultDto> resultData = result.getResult();
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = resultData.getData();

    // then
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.values())
      .hasSize(2)
      .extracting(AuthorizedProcessReportEvaluationResultDto::getResult)
      .flatExtracting(ReportMapResultDto::getData)
      .extracting(MapResultEntryDto::getKey)
      .hasSize(2)
      .first()
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_combinedDateReport_automaticInterval() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto instance1 = deployAndStartSimpleProcess();
    final ProcessInstanceEngineDto instance2 =
      engineIntegrationExtension.startProcessInstance(instance1.getDefinitionId());
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), now.plusDays(1));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(instance1.getProcessDefinitionKey())
      .setProcessDefinitionVersion(instance1.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId1 = reportClient.createSingleProcessReport(groupByDate);
    final String singleProcessReportId2 = reportClient.createSingleProcessReport(groupByDate);
    final String combinedReportId =
      reportClient.createNewCombinedReport(singleProcessReportId1, singleProcessReportId2);

    // when
    AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(combinedReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto>>() {});
    // @formatter:on
    final CombinedProcessReportResultDataDto<ReportMapResultDto> resultData = result.getResult();
    final Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> combinedResultMap =
      resultData.getData();

    // then
    assertThat(combinedResultMap).hasSize(2);
    for (AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> value : combinedResultMap.values()) {
      final ReportMapResultDto resultMap = value.getResult();
      final List<String> dateAsStringDateResultEntries = resultMap.getData()
        .stream()
        .map(MapResultEntryDto::getKey)
        .collect(Collectors.toList());

      String expectedDateAsString = embeddedOptimizeExtension.getDateTimeFormatter()
        .format(now.atZoneSameInstant(ZoneId.of("Europe/London")).toOffsetDateTime());
      assertThat(dateAsStringDateResultEntries)
        .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
      final String firstElement = dateAsStringDateResultEntries.get(0);
      assertThat(firstElement).isEqualTo(expectedDateAsString);

      expectedDateAsString = expectedLastDateAsStringForAutomaticInterval(now);
      final String lastElement = dateAsStringDateResultEntries.get(dateAsStringDateResultEntries.size() - 1);
      assertThat(lastElement).isEqualTo(expectedDateAsString);
    }
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_evaluationById() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId = reportClient.createSingleProcessReport(groupByDate);

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(singleProcessReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      .getResult();
      // @formatter:on

    // then
    assertThat(result.getData())
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_sharedReportEvaluation() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(groupByDate);
    final String reportShareId = sharingClient.shareReport(reportId);

    // when
    // @formatter:off
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      .getResult();
    // @formatter:on

    // then
    assertThat(result.getData())
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_reportEvaluationOfSharedDashboard() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(groupByDate);
    final String dashboardId = dashboardClient.createEmptyDashboard(null);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));
    final String dashboardShareId = sharingClient.shareDashboard(dashboardId);

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {
      })
      .getResult();

    // then
    assertThat(result.getData())
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(getOffsetDiffInHours(date, now)).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_rawProcessReport() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), now);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto rawDataReport = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processInstance.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(processInstance.getProcessDefinitionVersion()))
      .viewProperty(ProcessViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();

    // when
    final RawDataProcessReportResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(rawDataReport)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();

    // then
    assertThat(result.getData()).hasSize(1);
    RawDataProcessInstanceDto rawInstance = result.getData().get(0);
    assertThat(getOffsetDiffInHours(rawInstance.getStartDate(), now)).isOne();
    assertThat(getOffsetDiffInHours(rawInstance.getEndDate(), now)).isOne();
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_rawDecisionReport() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    DecisionDefinitionEngineDto decisionDefinition = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(now, now);
    importAllEngineEntitiesFromScratch();

    final DecisionReportDataDto rawDataReport = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinition.getKey())
      .setDecisionDefinitionVersion(decisionDefinition.getVersionAsString())
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();

    // when
    final RawDataDecisionReportResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(rawDataReport)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {})
      // @formatter:on
      .getResult();

    // then
    assertThat(result.getData()).hasSize(1);
    RawDataDecisionInstanceDto rawInstance = result.getData().get(0);
    assertThat(getOffsetDiffInHours(rawInstance.getEvaluationDateTime(), now)).isOne();
  }

  @Test
  public void adjustDatesInCSVExportToTimezone_byReportId() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), now);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    final String reportId = reportClient.createSingleProcessReport(groupByDate);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    String actualContent = getResponseContentAsString(response);
    OffsetDateTime londonTime = now.truncatedTo(ChronoUnit.HOURS)
      .atZoneSameInstant(ZoneId.of("Europe/London"))
      .toOffsetDateTime();
    String londonTimeAsString = embeddedOptimizeExtension.getDateTimeFormatter().format(londonTime);
    assertThat(actualContent).containsOnlyOnce(londonTimeAsString);
  }

  @Test
  public void adjustDatesInCSVExportToTimezone_byUnsavedRawProcessDataReport() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), now);
    importAllEngineEntitiesFromScratch();

    final ProcessRawDataCsvExportRequestDto exportRequestDto = ProcessRawDataCsvExportRequestDto.builder()
      .processDefinitionKey(processInstance.getProcessDefinitionKey())
      .processDefinitionVersions(Lists.newArrayList(ReportConstants.ALL_VERSIONS))
      .includedColumns(
        Lists.newArrayList(
          ProcessInstanceDto.Fields.startDate,
          ProcessInstanceDto.Fields.endDate
        )
      )
      .build();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDynamicRawProcessCsvExportRequest(exportRequestDto, "my_file.csv")
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    String actualContent = getResponseContentAsString(response);
    OffsetDateTime londonTime = now.atZoneSameInstant(ZoneId.of("Europe/London")).toOffsetDateTime();
    String londonTimeAsString = londonTime.toString();
    assertThat(StringUtils.countMatches(actualContent, londonTimeAsString)).isEqualTo(2);
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_reportDateFilter_fixedDate() {
    // given
    final OffsetDateTime now = OffsetDateTime.of(2019, 4, 15, 20, 0, 0, 0, ZoneOffset.UTC);
    ProcessInstanceDto instanceDto = createTwoProcessInstancesWithStartDate(now);

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(instanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    List<ProcessFilterDto<?>> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        // the offset of the filter should be respected
        .start(now.withOffsetSameInstant(ZoneOffset.ofHours(+18)))
        .end(now.plusHours(1).withOffsetSameInstant(ZoneOffset.ofHours(+18)))
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // timezone that should be used for the filter is adjusted as well
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "UTC")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      // @formatter:on
      .getResult();

    // then there should be a result
    assertThat(result.getData())
      .hasSize(1)
      .last()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(2.0);
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_dateHistogramFilterBucketLimiting() {
    // given
    final OffsetDateTime now = OffsetDateTime.of(2019, 4, 15, 20, 0, 0, 0, ZoneOffset.UTC);
    ProcessInstanceDto instanceDto = createTwoProcessInstancesWithStartDate(now);

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.MONTH)
      .setProcessDefinitionKey(instanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(instanceDto.getProcessDefinitionVersion())
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // I adjust timezone that should be used for the filter as well
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/Berlin")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      // @formatter:on
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData())
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(2.0);
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_reportDateFilter_relativeDate() {
    // given
    // the timezone of the server is berlin time
    final OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    // the instance is truncated to the beginning of the year
    final OffsetDateTime instanceStartDate =
      truncateToStartOfUnit(now, ChronoUnit.YEARS, ZoneId.of("Europe/Berlin")).toOffsetDateTime();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), instanceStartDate);
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.YEAR)
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    List<ProcessFilterDto<?>> relativeStartDateFilter =
      ProcessFilterBuilder.filter()
        .relativeStartDate()
        // add a relative date filter for this year
        .start(0L, DateFilterUnit.YEARS)
        .add()
        .buildList();
    reportData.setFilter(relativeStartDateFilter);

    // when
    final ReportMapResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // difference between UTC and Berlin time is +1 (UTC) or +2 (UTC DST)
      // this offset will be subtracted from the given date. Since the only instance
      // is in beginning of the year Berlin time it will fall into the year before.
      // Thus, the instance will not be part of the result if the timezone of this request is respected.
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "UTC")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      // @formatter:on
      .getResult();

    // then
    // if the timezone of the request was not respected then the result would be not be empty
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void branchAnalysis_adjustsFilterToTimezone() {
    // given
    // the timezone of the server is berlin time
    final OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    // the instance is truncated to the beginning of the year
    final OffsetDateTime instanceStartDate =
      truncateToStartOfUnit(now, ChronoUnit.YEARS, ZoneId.of("Europe/Berlin")).toOffsetDateTime();
    final ProcessDefinitionEngineDto gatewayDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(gatewayDefinition.getId(), ImmutableMap.of("goToTask1", true));
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), instanceStartDate);
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto branchAnalysisRequestDto = analysisClient.createAnalysisDto(
      gatewayDefinition.getKey(),
      Lists.newArrayList(String.valueOf(gatewayDefinition.getVersion())),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT
    );

    List<ProcessFilterDto<?>> relativeStartDateFilter =
      ProcessFilterBuilder.filter()
        .relativeStartDate()
        // add a relative date filter for this year
        .start(0L, DateFilterUnit.YEARS)
        .add()
        .buildList();
    branchAnalysisRequestDto.setFilter(relativeStartDateFilter);

    // when
    final BranchAnalysisResponseDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "UTC")
      .buildProcessDefinitionCorrelation(branchAnalysisRequestDto)
      .execute(BranchAnalysisResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    // if the timezone of the request was not respected then the result would be not be empty
    assertThat(result.getTotal()).isZero();
  }

  private List<String> evaluateReportInLondonTimezoneAndReturnDateEntries(final ProcessReportDataDto reportData) {
    final ProcessReportResultDto result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ProcessReportResultDto>>() {})
      // @formatter:on
      .getResult();
    assertThat(result).isNotNull();
    if (result instanceof ReportHyperMapResultDto) {
      ReportHyperMapResultDto hyperMapResultDto = (ReportHyperMapResultDto) result;
      if (reportData.getDistributedBy().getValue() instanceof DateDistributedByValueDto) {
        return hyperMapResultDto.getData().stream()
          .flatMap(hyperEntry -> hyperEntry.getValue().stream())
          .map(MapResultEntryDto::getKey)
          .collect(Collectors.toList());
      } else {
        return hyperMapResultDto.getData().stream().map(HyperMapResultEntryDto::getKey).collect(Collectors.toList());
      }
    } else if (result instanceof ReportMapResultDto) {
      ReportMapResultDto reportMapResultDto = (ReportMapResultDto) result;
      return reportMapResultDto.getData().stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    } else {
      throw new OptimizeIntegrationTestException("Unknown result type!");
    }
  }

  private ProcessInstanceDto createTwoProcessInstancesWithStartDate(final OffsetDateTime date) {
    // we need to add the data by hand to ensure that the date is stored
    // with the timezone given in the date parameter
    ProcessInstanceDto instanceDto = ProcessInstanceDto.builder()
      .processInstanceId("123")
      .processDefinitionKey("aKey")
      .processDefinitionVersion("1")
      .startDate(date)
      .endDate(date)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_INSTANCE_INDEX_NAME,
      instanceDto.getProcessInstanceId(),
      instanceDto
    );
    instanceDto.setProcessInstanceId("124");
    instanceDto.setStartDate(date.plusHours(1));
    instanceDto.setEndDate(date.plusHours(1));
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_INSTANCE_INDEX_NAME,
      instanceDto.getProcessInstanceId(),
      instanceDto
    );
    return instanceDto;
  }

  private OffsetDateTime truncateToYearWithLondonTimezone(final OffsetDateTime now) {
    return truncateToStartOfUnit(now, ChronoUnit.YEARS, (ZoneId.of("Europe/London"))).toOffsetDateTime();
  }

  private String expectedLastDateAsStringForAutomaticInterval(final OffsetDateTime now) {
    final String expectedDateAsString;
    final long automaticIntervalStepInMs = Duration.between(now, now.plusDays(1))
      .toMillis() / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    final OffsetDateTime lastDateOfAutomaticInterval = now.plusDays(1)
      .minus(automaticIntervalStepInMs, ChronoUnit.MILLIS);
    expectedDateAsString = embeddedOptimizeExtension.getDateTimeFormatter()
      .format(lastDateOfAutomaticInterval.atZoneSameInstant(ZoneId.of("Europe/London")).toOffsetDateTime());
    return expectedDateAsString;
  }

}
