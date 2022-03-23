/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.ProcessRawDataCsvExportRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.HyperMapMeasureResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MapMeasureResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
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
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
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
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
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
    final String clientTimezone = "Europe/London";
    final List<String> dateAsStringDateResultEntries = evaluateReportInClientTimezoneAndReturnDateEntries(
      reportData, clientTimezone);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
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
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeAllFlowNodeStartDates(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeAllFlowNodeEndDates(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
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
    final List<String> dateAsStringDateResultEntries =
      evaluateReportInClientTimezoneAndReturnDateEntries(reportData, "Europe/London");

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
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_RUNNING_DATE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .build();

    // when
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // the timezone has an offset of -1(UTC)/-2 (UTC DST) and if the truncation is wrong
      // the result date would fall into the year 2019
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Atlantic/Cape_Verde")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      // @formatter:on
      .getResult().getFirstMeasureData();

    // then
    OffsetDateTime expectedDate =
      ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Atlantic/Cape_Verde")).toOffsetDateTime();
    assertThat(resultData)
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
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final ProcessInstanceEngineDto processInstanceDto2 = deployAndStartSimpleUserTaskProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto1.getId(), now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), START_EVENT, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto2.getId(), START_EVENT, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), START_EVENT, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto2.getId(), START_EVENT, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto2.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), USER_TASK_1, now);
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto1.getId(), END_EVENT, now);
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto1.getId(), END_EVENT, now);
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
    final String clientTimezone = "Europe/London";
    final List<String> dateAsStringDateResultEntries = evaluateReportInClientTimezoneAndReturnDateEntries(
      reportData, clientTimezone);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
  }

  @Test
  public void unsavedReportEvaluationDoesNotFailWithZOffsetLastModifiedDateFormat() throws JsonProcessingException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();
    final SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(reportData);
    reportDef.setLastModified(OffsetDateTime.parse("2021-01-05T10:25:16.161Z"));

    // when
    final String requestBodyAsString = embeddedOptimizeExtension.getObjectMapper().copy()
      // allow to overwrite previous registration of the JavaTimeModule with custom de-/serialization for OffsetDateTime
      .disable(MapperFeature.IGNORE_DUPLICATE_MODULE_REGISTRATIONS)
      .registerModule(new JavaTimeModule())
      .writeValueAsString(reportDef);
    // ensure the body is serialized as intended
    assertThat(requestBodyAsString).contains("161Z");

    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGenericRequest(
        "POST", "report/evaluate", Entity.entity(requestBodyAsString, MediaType.APPLICATION_JSON_TYPE)
      )
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
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
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
    final String clientTimezone = "Europe/London";
    final List<String> dateAsStringDateResultEntries = evaluateReportInClientTimezoneAndReturnDateEntries(
      reportData, clientTimezone);

    // then
    assertThat(dateAsStringDateResultEntries)
      .hasSize(1)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
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
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    // when
    final String clientTimezone = "Europe/London";
    final List<String> dateAsStringDateResultEntries = evaluateReportInClientTimezoneAndReturnDateEntries(
      reportData, clientTimezone);

    // then
    String expectedDateAsString = embeddedOptimizeExtension.getDateTimeFormatter()
      .format(now.atZoneSameInstant(ZoneId.of(clientTimezone)).toOffsetDateTime());
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
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
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
    final String clientTimezone = "Europe/London";
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      .getResult()
      .getFirstMeasureData();
      // @formatter:on

    // then
    assertThat(resultData)
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey)
      .first()
      .extracting(a -> OffsetDateTime.parse(a, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_combinedDateReport() {
    // given
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId1 = reportClient.createSingleProcessReport(groupByDate);
    final String singleProcessReportId2 = reportClient.createSingleProcessReport(groupByDate);
    final String combinedReportId =
      reportClient.createNewCombinedReport(singleProcessReportId1, singleProcessReportId2);

    // when
    final String clientTimezone = "Europe/London";
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(combinedReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>>>() {});
      // @formatter:on
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> resultData = result.getResult();
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> resultMap =
      resultData.getData();

    // then
    assertThat(resultMap).hasSize(2);
    assertThat(resultMap.values())
      .hasSize(2)
      .extracting(AuthorizedProcessReportEvaluationResponseDto::getResult)
      .flatExtracting(ReportResultResponseDto::getFirstMeasureData)
      .extracting(MapResultEntryDto::getKey)
      .hasSize(2)
      .first()
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId1 = reportClient.createSingleProcessReport(groupByDate);
    final String singleProcessReportId2 = reportClient.createSingleProcessReport(groupByDate);
    final String combinedReportId =
      reportClient.createNewCombinedReport(singleProcessReportId1, singleProcessReportId2);

    // when
    AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(combinedReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResponseDto<List<MapResultEntryDto>>>() {});
    // @formatter:on
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> resultData = result.getResult();
    final Map<String, AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>> combinedResultMap =
      resultData.getData();

    // then
    assertThat(combinedResultMap).hasSize(2);
    for (AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> value : combinedResultMap.values()) {
      final List<String> dateAsStringDateResultEntries = value.getResult().getFirstMeasureData()
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
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String singleProcessReportId = reportClient.createSingleProcessReport(groupByDate);

    // when
    final String clientTimezone = "Europe/London";
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(singleProcessReportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      .getResult()
      .getFirstMeasureData();
      // @formatter:on

    // then
    assertThat(resultData)
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_sharedReportEvaluation() {
    // given
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(groupByDate);
    final String reportShareId = sharingClient.shareReport(reportId);

    // when
    // @formatter:off
    final String clientTimezone = "Europe/London";
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      .getResult()
      .getFirstMeasureData();
    // @formatter:on

    // then
    assertThat(resultData)
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_reportEvaluationOfSharedDashboard() {
    // given
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(engineDto.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(engineDto.getId(), now.minusDays(2L));
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto groupByDate = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setGroupByDateInterval(AggregateByDateUnit.HOUR)
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(groupByDate);
    final String dashboardId = dashboardClient.createEmptyDashboard(null);
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));
    final String dashboardShareId = sharingClient.shareDashboard(dashboardId);

    // when
    final String clientTimezone = "Europe/London";
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {
      })
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData)
      .hasSize(1)
      .first()
      .extracting(MapResultEntryDto::getKey)
      .extracting(date -> OffsetDateTime.parse(date, embeddedOptimizeExtension.getDateTimeFormatter()))
      .satisfies(date -> assertThat(offsetDiffForZonedDateTimes(
        toZonedDateTime(now, systemTimezone),
        toZonedDateTime(date, clientTimezone)
      )).isOne());
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_rawProcessReport() {
    // given
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), now);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto rawDataReport = new ProcessReportDataBuilderHelper()
      .processDefinitionKey(processInstance.getProcessDefinitionKey())
      .processDefinitionVersions(Collections.singletonList(processInstance.getProcessDefinitionVersion()))
      .viewProperty(ViewProperty.RAW_DATA)
      .visualization(ProcessVisualization.TABLE)
      .build();

    // when
    final String clientTimezone = "Europe/London";
    final List<RawDataProcessInstanceDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(rawDataReport)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>>>() {})
      // @formatter:on
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);
    RawDataProcessInstanceDto rawInstance = resultData.get(0);
    assertThat(offsetDiffForZonedDateTimes(
      toZonedDateTime(now, systemTimezone),
      toZonedDateTime(rawInstance.getStartDate(), clientTimezone)
    )).isOne();
    assertThat(offsetDiffForZonedDateTimes(
      toZonedDateTime(now, systemTimezone),
      toZonedDateTime(rawInstance.getEndDate(), clientTimezone)
    )).isOne();
  }

  @Test
  public void adjustReportEvaluationResultToTimezone_rawDecisionReport() {
    // given
    final String systemTimezone = "Europe/Berlin";
    OffsetDateTime now = dateFreezer().timezone(systemTimezone).freezeDateAndReturn();
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
    final String clientTimezone = "Europe/London";
    final List<RawDataDecisionInstanceDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(rawDataReport)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>>() {})
      // @formatter:on
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);
    RawDataDecisionInstanceDto rawInstance = resultData.get(0);
    assertThat(offsetDiffForZonedDateTimes(
      toZonedDateTime(now, systemTimezone),
      toZonedDateTime(rawInstance.getEvaluationDateTime(), clientTimezone)
    )).isOne();
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    List<ProcessFilterDto<?>> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedInstanceStartDate()
        // the offset of the filter should be respected
        .start(now.withOffsetSameInstant(ZoneOffset.ofHours(+18)))
        .end(now.plusHours(1).withOffsetSameInstant(ZoneOffset.ofHours(+18)))
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);

    // when
    final List<MapResultEntryDto> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // timezone that should be used for the filter is adjusted as well
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "UTC")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      // @formatter:on
      .getResult()
      .getFirstMeasureData();

    // then there should be a result
    assertThat(result)
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // I adjust timezone that should be used for the filter as well
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/Berlin")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      // @formatter:on
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
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
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();

    List<ProcessFilterDto<?>> relativeStartDateFilter =
      ProcessFilterBuilder.filter()
        .relativeInstanceStartDate()
        // add a relative date filter for this year
        .start(0L, DateUnit.YEARS)
        .add()
        .buildList();
    reportData.setFilter(relativeStartDateFilter);

    // when
    final List<MapResultEntryDto> resultData = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // difference between UTC and Berlin time is +1 (UTC) or +2 (UTC DST)
      // this offset will be subtracted from the given date. Since the only instance
      // is in beginning of the year Berlin time it will fall into the year before.
      // Thus, the instance will not be part of the result if the timezone of this request is respected.
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "UTC")
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>>>() {})
      // @formatter:on
      .getResult()
      .getFirstMeasureData();

    // then
    // if the timezone of the request was not respected then the result would be not be empty
    assertThat(resultData).isEmpty();
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
        .relativeInstanceStartDate()
        // add a relative date filter for this year
        .start(0L, DateUnit.YEARS)
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

  private List<String> evaluateReportInClientTimezoneAndReturnDateEntries(final ProcessReportDataDto reportData,
                                                                          final String clientTimezone) {
    final MeasureResponseDto<?> result = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, clientTimezone)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResponseDto<?>>() {})
      // @formatter:on
      .getResult().getMeasures().get(0);
    assertThat(result).isNotNull();
    if (result instanceof HyperMapMeasureResponseDto) {
      HyperMapMeasureResponseDto hyperMapResultDto = (HyperMapMeasureResponseDto) result;
      if (reportData.getDistributedBy().getValue() instanceof DateDistributedByValueDto) {
        return hyperMapResultDto.getData().stream()
          .flatMap(hyperEntry -> hyperEntry.getValue().stream())
          .map(MapResultEntryDto::getKey)
          .collect(Collectors.toList());
      } else {
        return hyperMapResultDto.getData().stream().map(HyperMapResultEntryDto::getKey).collect(Collectors.toList());
      }
    } else if (result instanceof MapMeasureResponseDto) {
      MapMeasureResponseDto reportMapResultDto = (MapMeasureResponseDto) result;
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
    embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .createIndexIfMissing(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient(),
        new ProcessInstanceIndex("aKey")
      );
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      getProcessInstanceIndexAliasName("aKey"),
      instanceDto.getProcessInstanceId(),
      instanceDto
    );
    instanceDto.setProcessInstanceId("124");
    instanceDto.setStartDate(date.plusHours(1));
    instanceDto.setEndDate(date.plusHours(1));
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      getProcessInstanceIndexAliasName("aKey"),
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

  private ZonedDateTime toZonedDateTime(final OffsetDateTime offsetDateTime, final String timezone) {
    return offsetDateTime.atZoneSameInstant(TimeZone.getTimeZone(timezone).toZoneId());
  }

  // Compares the first parameter to the second. So will be positive if the offset of z1 is greater than z2 after
  // daylight savings have been considered, and negative if its offset is less than that of z2
  private static double offsetDiffForZonedDateTimes(final ZonedDateTime z1, final ZonedDateTime z2) {
    int offsetDiffInSeconds = z1.getOffset().getTotalSeconds() - z2.getOffset().getTotalSeconds();
    double offsetInHours = offsetDiffInSeconds / 3600.0; // convert to hours
    // We adjust the calculation to consider DST differences
    final boolean z1IsDaylightSaving = z1.getZone().getRules().isDaylightSavings(z1.toInstant());
    final boolean z2IsDaylightSaving = z2.getZone().getRules().isDaylightSavings(z2.toInstant());
    if (z1IsDaylightSaving && !z2IsDaylightSaving) {
      offsetInHours -= 1;
    } else if (!z1IsDaylightSaving && z2IsDaylightSaving) {
      offsetInHours += 1;
    }
    return offsetInHours;
  }

}
