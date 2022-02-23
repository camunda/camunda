/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.date;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.DateDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.*;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractProcessInstanceDurationByVariableByDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract DistributedByType getDistributeByType();

  @Test
  public void simpleReportEvaluation() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcessWithStringVariable();
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(getDistributeByType());
    assertThat(((DateDistributedByValueDto) resultReportDataDto.getDistributedBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcessWithStringVariable();
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(getDistributeByType());
    assertThat(((DateDistributedByValueDto) resultReportDataDto.getDistributedBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = evaluationResponse.getResult();
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcessWithStringVariable();
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "another string"));
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 2L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfReferenceDate), 1000.)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByDateWorksForAllStaticUnits_withStringVariable(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    // setting every unit section to its center to avoid bucket shifting due duration modifications
    final OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcessWithStringVariable();
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar", unit);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime truncatedReferenceDate = truncateToStartOfUnit(referenceDate, chronoUnit);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(truncatedReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByDateWorksForAllStaticUnits_withDateVariable(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    // setting every unit section to its center to avoid bucket shifting due duration modifications
    final OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "dateVar",
      referenceDate
    ));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(
      procInstance1,
      VariableType.DATE,
      "dateVar",
      unit
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime truncatedReferenceDate = truncateToStartOfUnit(referenceDate, chronoUnit);
    final ZonedDateTime truncatedVariableDate = truncateToStartOfUnit(referenceDate, ChronoUnit.MONTHS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains(localDateTimeToString(truncatedVariableDate))
          .distributedByContains(localDateTimeToString(truncatedReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByDateWorksForAllStaticUnits_withNumberVariable(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    // setting every unit section to its center to avoid bucket shifting due duration modifications
    final OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "doubleVar",
      10.0
    ));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar", unit);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime truncatedReferenceDate = truncateToStartOfUnit(referenceDate, chronoUnit);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains("10.00")
          .distributedByContains(localDateTimeToString(truncatedReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void emptyBucketsIncludeAllDistrByKeys() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "doubleVar",
      1.0
    ));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 = engineIntegrationExtension.startProcessInstance(
      procInstance1.getDefinitionId(),
      Collections.singletonMap(
        "doubleVar",
        3.0
      )
    );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 2L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(1.0);
    reportData.getConfiguration().getCustomBucket().setBucketSize(1.0);
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then the bucket "2.0" has all distrBy keys that the other buckets have
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = evaluationResponse.getResult();
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains("1.00")
          .distributedByContains(localDateTimeToString(startOfToday), 1000.)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), null)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), null)
        .groupByContains("2.00") // this empty bucket includes all distrBy keys despite all distrBy values being null
          .distributedByContains(localDateTimeToString(startOfToday), null)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), null)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), null)
        .groupByContains("3.00")
          .distributedByContains(localDateTimeToString(startOfToday), null)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), null)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), 1000.)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @Test
  public void distributeByDateWorksForAutomaticInterval_WithStringVariable() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcessWithStringVariable();
    final ProcessInstanceEngineDto procInstance2 =
      startProcessInstanceWithStringVariable(procInstance1.getDefinitionId());

    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate.plusDays(1), 0, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(
      procInstance1,
      VariableType.STRING,
      "stringVar",
      AggregateByDateUnit.AUTOMATIC
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    final List<MapResultEntryDto> resultEntries = result.getFirstMeasureData().get(0).getValue();
    assertThat(resultEntries)
      .extracting(MapResultEntryDto::getValue)
      .filteredOn(Objects::nonNull)
      .containsExactly(1000., 2000.);
  }

  @SneakyThrows
  @Test
  public void distributeByDateWorksForAutomaticInterval_WithDateVariable() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "dateVar",
      referenceDate
    ));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);
    final ProcessInstanceEngineDto procInstance2 = engineIntegrationExtension.startProcessInstance(
      procInstance1.getDefinitionId(),
      Collections.singletonMap(
        "dateVar",
        referenceDate
      )
    );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate.plusDays(1), 0, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(
      procInstance1,
      VariableType.DATE,
      "dateVar",
      AggregateByDateUnit.AUTOMATIC
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    final List<MapResultEntryDto> resultEntries = result.getFirstMeasureData().get(0).getValue();
    assertThat(resultEntries)
      .extracting(MapResultEntryDto::getValue)
      .filteredOn(Objects::nonNull)
      .containsExactly(1000., 2000.);
  }

  @SneakyThrows
  @Test
  public void distributeByDateWorksForAutomaticInterval_WithNumberVariable() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "doubleVar",
      10.0
    ));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);
    final ProcessInstanceEngineDto procInstance2 = engineIntegrationExtension.startProcessInstance(
      procInstance1.getDefinitionId(),
      Collections.singletonMap(
        "doubleVar",
        10.0
      )
    );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate.plusDays(1), 0, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(
      procInstance1,
      VariableType.DOUBLE,
      "doubleVar",
      AggregateByDateUnit.AUTOMATIC
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    final List<MapResultEntryDto> resultEntries = result.getFirstMeasureData().get(0).getValue();
    assertThat(resultEntries)
      .extracting(MapResultEntryDto::getValue)
      .filteredOn(Objects::nonNull)
      .containsExactly(1000., 2000.);
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto instance1 = deployAndStartSimpleProcessWithStringVariable();
    adjustProcessInstanceDatesAndDuration(instance1.getId(), referenceDate, 0, 1L);
    final ProcessInstanceEngineDto instance2 = startProcessInstanceWithStringVariable(instance1.getDefinitionId());
    adjustProcessInstanceDatesAndDuration(instance2.getId(), referenceDate, -2L, 9L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(instance1, VariableType.STRING, "stringVar");
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AVERAGE)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfReferenceDate.minusDays(2)), 9000.0)
          .distributedByContains(localDateTimeToString(startOfReferenceDate.minusDays(1)), null)
          .distributedByContains(localDateTimeToString(startOfReferenceDate), 1000.0)
      .doAssert(result);
    // @formatter:on
  }

  protected ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                  final VariableType variableType,
                                                  final String variableName) {
    return createReportData(processInstanceDto, variableType, variableName, AggregateByDateUnit.DAY);
  }

  protected ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName,
                                                final AggregateByDateUnit distributeByDateUnit) {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setDistributeByDateInterval(distributeByDateUnit)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableType(variableType)
      .setVariableName(variableName)
      .build();
  }

  private ProcessInstanceEngineDto startProcessInstanceWithStringVariable(final String definitionId) {
    return engineIntegrationExtension.startProcessInstance(definitionId, Collections.singletonMap(
      "stringVar",
      "a string"
    ));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithStringVariable() {
    return deployAndStartSimpleProcess(Collections.singletonMap(
      "stringVar",
      "a string"
    ));
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
  }

  protected void adjustProcessInstanceDatesAndDuration(final String processInstanceId,
                                                       final OffsetDateTime referenceDate,
                                                       final long daysToShift,
                                                       final Long durationInSec) {
    final OffsetDateTime shiftedEndDate = referenceDate.plusDays(daysToShift);
    if (durationInSec != null) {
      engineDatabaseExtension.changeProcessInstanceStartDate(
        processInstanceId,
        shiftedEndDate.minusSeconds(durationInSec)
      );
    }
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, shiftedEndDate);
  }
}
