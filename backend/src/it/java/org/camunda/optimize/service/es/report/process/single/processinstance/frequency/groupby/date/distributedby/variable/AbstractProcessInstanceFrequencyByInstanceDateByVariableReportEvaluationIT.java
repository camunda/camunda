/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.variable;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.getTypeForId;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractProcessInstanceFrequencyByInstanceDateByVariableReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract void changeProcessInstanceDate(final String processInstanceId,
                                                    final OffsetDateTime newDate);

  @Test
  public void simpleReportEvaluation() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(AggregateByDateUnit.DAY);
    assertThat(resultReportDataDto.getDistributedBy().getType())
      .isEqualTo(DistributedByType.VARIABLE);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);
    assertThat(resultReportDataDto.getDistributedBy().getType())
      .isEqualTo(DistributedByType.VARIABLE);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void customOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "another string")
      );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate.minusDays(1));

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "this is also a string")
      );
    changeProcessInstanceDate(procInstance3.getId(), referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final String reportId = createNewReport(reportData);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReportById(
      reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
          .distributedByContains("another string", 0.0)
          .distributedByContains("this is also a string", 0.0)
        .groupByContains(localDateTimeToString(startOfToday.minusDays(1)))
          .distributedByContains("a string", 0.0)
          .distributedByContains("another string", 1.0)
          .distributedByContains("this is also a string", 0.0)
        .groupByContains(localDateTimeToString(startOfToday.minusDays(2)))
          .distributedByContains("a string", 0.0)
          .distributedByContains("another string", 0.0)
          .distributedByContains("this is also a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", 1.0)
      );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReportById(
      reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
          .distributedByContains(MISSING_VARIABLE_KEY, 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void otherVariablesDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar1", "a string");
    variables.put("stringVar2", "another string");
    final ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcess(variables);
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar1");
    final String reportId = createNewReport(reportData);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReportById(
      reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    Map<String, Object> variables = new HashMap<>();
    variables.put(VariableType.DATE.getId(), referenceDate);
    variables.put(VariableType.BOOLEAN.getId(), true);
    variables.put(VariableType.SHORT.getId(), (short) 2);
    variables.put(VariableType.INTEGER.getId(), 3);
    variables.put(VariableType.LONG.getId(), 4L);
    variables.put(VariableType.DOUBLE.getId(), 5.5);
    variables.put(VariableType.STRING.getId(), "aString");
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleProcess(variables);
    changeProcessInstanceDate(processInstanceDto.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = getTypeForId(entry.getKey());
      ProcessReportDataDto reportData = createReportData(processInstanceDto, variableType, entry.getKey());
      final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(
        reportData).getResult();

      // then
      assertThat(result.getFirstMeasureData()
                   .stream()
                   .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                   .mapToDouble(MapResultEntryDto::getValue)
                   .sum())
        .withFailMessage("Failed instance count assertion on variable " + entry.getKey())
        .isEqualTo(1.0);
      assertThat(result.getFirstMeasureData()
                   .stream()
                   .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                   .map(MapResultEntryDto::getKey))
        .withFailMessage("Failed bucket key assertion on variable " + entry.getKey())
        .containsOnlyOnce(getExpectedKeyForVariableType(variableType, entry.getValue()));
    }
  }

  @Test
  public void worksWithVariablesWithMultipleValues() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final VariableDto listVar = variablesClient.createListJsonObjectVariableDto(List.of("value1", "value2"));
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("listVar", listVar));
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "listVar");
    final String reportId = createNewReport(reportData);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
      reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("value1", 1.0)
        .distributedByContains("value2", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByDateVariableWorksForAllStaticUnits(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("dateVar", dateVariableValue));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DATE, "dateVar");
    reportData.getConfiguration().setDistributeByDateVariableUnit(unit);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    final ZonedDateTime truncatedReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    final ZonedDateTime truncatedDateVariableValue = truncateToStartOfUnit(dateVariableValue, chronoUnit);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(truncatedReferenceDate))
          .distributedByContains(localDateTimeToString(truncatedDateVariableValue), 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @Test
  public void distributeByDateVariableWorksForAutomaticIntervalSelection() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("dateVar", dateVariableValue));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("dateVar", dateVariableValue.plusDays(1))
      );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate.plusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DATE, "dateVar");
    reportData.getConfiguration().setDistributeByDateVariableUnit(AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then result has 80 buckets each and they include both instances
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .hasSize(2);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .allSatisfy(
        resultEntries -> assertThat(resultEntries).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      );
    assertThat(result.getFirstMeasureData()
                 .stream()
                 .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
      .isEqualTo(2.0);
  }

  @Test
  public void numberVariable_customBuckets() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("doubleVar", 200.0)
      );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("doubleVar", 300.0)
      );
    changeProcessInstanceDate(procInstance3.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    reportData.getConfiguration().setDistributeByCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(50.0)
        .bucketSize(100.0)
        .build()
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("50.00", 1.0)
          .distributedByContains("150.00", 1.0)
          .distributedByContains("250.00", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void numberVariable_invalidBaseline_returnsEmptyResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when the baseline is larger than the max. variable value
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    reportData.getConfiguration().setDistributeByCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(200.0)
        .bucketSize(100.0)
        .build()
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then the report returns an empty distrBy result
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    changeProcessInstanceDate(procInstance.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.DOUBLE, "doubleVar");
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey)
      .isNotEmpty()
      .allMatch(key -> key.length() - key.indexOf(".") - 1 == 2); // key should have two chars after the decimal
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given 1 instance with "stringVar"
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    // and 4 instances without "stringVar"
    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(procInstance1.getDefinitionId());
    changeProcessInstanceDate(procInstance2.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", null)
      );
    changeProcessInstanceDate(procInstance3.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance4 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", new EngineVariableValue(null, VariableType.STRING.getId()))
      );
    changeProcessInstanceDate(procInstance4.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance5 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("anotherVar", "another string")
      );
    changeProcessInstanceDate(procInstance5.getId(), referenceDate);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReportById(
      reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(5L)
      .processInstanceCountWithoutFilters(5L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains("a string", 1.0)
          .distributedByContains(MISSING_VARIABLE_KEY, 4.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void worksForAutomaticIntervalSelection() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcess(Collections.singletonMap(
      "boolVar",
      true
    ));
    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(procInstance.getDefinitionId(), Collections.singletonMap(
        "boolVar",
        true
      ));

    changeProcessInstanceDate(procInstance.getId(), referenceDate);
    changeProcessInstanceDate(procInstance2.getId(), referenceDate.plusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReportData(procInstance, VariableType.BOOLEAN, "boolVar", AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then a non-empty result is returned with instances in the first and last bucket
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getFirstMeasureData()).isNotEmpty();

    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .first()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.0);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .last()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.0);
  }


  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName) {
    return createReportData(processInstanceDto, variableType, variableName, AggregateByDateUnit.DAY);
  }

  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName,
                                                final AggregateByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setGroupByDateInterval(groupByDateUnit)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableType(variableType)
      .setVariableName(variableName)
      .build();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleServiceTaskProcess());
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
  }

  @SneakyThrows
  private String getExpectedKeyForVariableType(final VariableType variableType, final Object variableValue) {
    switch (variableType) {
      case STRING:
      case INTEGER:
      case BOOLEAN:
      case LONG:
      case SHORT:
        return String.valueOf(variableValue);
      case DOUBLE:
        DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
        final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);
        return decimalFormat.format(variableValue);
      case DATE:
        final OffsetDateTime temporal = (OffsetDateTime) variableValue;
        return embeddedOptimizeExtension.formatToHistogramBucketKey(
          temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
          ChronoUnit.MONTHS
        );
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type");
    }
  }

}