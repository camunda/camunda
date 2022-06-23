/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.date;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
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
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractProcessInstanceFrequencyByVariableByInstanceDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract DistributedByType getDistributeByType();

  protected abstract void changeProcessInstanceDate(final String processInstanceId, final OffsetDateTime newDate);

  @Test
  public void simpleReportEvaluation() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcessWithStringVariable();
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
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
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(getDistributeByType());
    assertThat(((DateDistributedByValueDto) resultReportDataDto.getDistributedBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfToday), 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcessWithStringVariable();
    changeProcessInstanceDate(procInstance.getId(), referenceDate);
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
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.VARIABLE);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(getDistributeByType());
    assertThat(((DateDistributedByValueDto) resultReportDataDto.getDistributedBy().getValue()).getUnit())
      .isEqualTo(AggregateByDateUnit.DAY);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = evaluationResponse.getResult();
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfToday), 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByStaticDateUnits_WithStringVariables(final AggregateByDateUnit unit) {
    // given
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcessWithStringVariable();
    final OffsetDateTime startOfToday = dateFreezer().freezeDateAndReturn();
    changeProcessInstanceDate(procInst1.getId(), startOfToday);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.STRING,
      "stringVar",
      unit
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime truncatedDate = truncateToStartOfUnit(startOfToday, mapToChronoUnit(unit));
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    // formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(truncatedDate), 1.0)
      .doAssert(result);
    // formatter:on
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void distributeByStaticDateUnits_WithNumberVariables(final AggregateByDateUnit unit) {
    // given
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcess(
      Collections.singletonMap(
        "numberVar",
        1
      ));
    final OffsetDateTime startOfToday = dateFreezer().freezeDateAndReturn();
    changeProcessInstanceDate(procInst1.getId(), startOfToday);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.INTEGER,
      "numberVar",
      unit
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime truncatedDate = truncateToStartOfUnit(startOfToday, mapToChronoUnit(unit));
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    // formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("1")
          .distributedByContains(localDateTimeToString(truncatedDate), 1.0)
      .doAssert(result);
    // formatter:on
  }

  @Test
  public void emptyBucketsIncludeAllDistributedByKeys() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 = deployAndStartSimpleProcess(Collections.singletonMap(
      "doubleVar",
      1.0
    ));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 = engineIntegrationExtension.startProcessInstance(
      procInstance1.getDefinitionId(),
      Collections.singletonMap(
        "doubleVar",
        3.0
      )
    );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate.plusDays(2));

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
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("1.00")
          .distributedByContains(localDateTimeToString(startOfToday), 1.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), 0.0)
        .groupByContains("2.00") // this empty bucket includes all distrBy keys despite all distrBy values being 0.0
          .distributedByContains(localDateTimeToString(startOfToday), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), 0.0)
        .groupByContains("3.00")
          .distributedByContains(localDateTimeToString(startOfToday), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(1)), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday.plusDays(2)), 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void automaticIntervalSelectionWorks_WithStringVariables() {
    // given
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcessWithStringVariable();
    final ProcessInstanceEngineDto procInst2 = startInstanceWithStringVariable(procInst1.getDefinitionId());
    final ProcessInstanceEngineDto procInst3 = startInstanceWithStringVariable(procInst1.getDefinitionId());

    final OffsetDateTime startOfToday = dateFreezer().freezeDateAndReturn().truncatedTo(ChronoUnit.DAYS);
    changeProcessInstanceDate(procInst1.getId(), startOfToday);
    changeProcessInstanceDate(procInst2.getId(), startOfToday);
    changeProcessInstanceDate(procInst3.getId(), startOfToday.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.STRING,
      "stringVar",
      AggregateByDateUnit.AUTOMATIC
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .hasSize(1);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .allSatisfy(
        resultEntries -> assertThat(resultEntries).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      );
    assertThat(result.getFirstMeasureData().stream()
                 .flatMap(e -> e.getValue().stream())
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
      .isEqualTo(3.0);
  }

  @Test
  public void automaticIntervalSelectionWorks_WithNumberVariables() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "numberVar",
      1.0
    );
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcess(variables);
    final ProcessInstanceEngineDto procInst2 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      variables
    );
    final ProcessInstanceEngineDto procInst3 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      variables
    );

    final OffsetDateTime startOfToday = dateFreezer().freezeDateAndReturn().truncatedTo(ChronoUnit.DAYS);
    changeProcessInstanceDate(procInst1.getId(), startOfToday);
    changeProcessInstanceDate(procInst2.getId(), startOfToday);
    changeProcessInstanceDate(procInst3.getId(), startOfToday.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.DOUBLE,
      "numberVar",
      AggregateByDateUnit.AUTOMATIC
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .hasSize(1);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .allSatisfy(
        resultEntries -> assertThat(resultEntries).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      );
    assertThat(result.getFirstMeasureData().stream()
                 .flatMap(e -> e.getValue().stream())
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
      .isEqualTo(3.0);
  }

  @Test
  public void automaticIntervalSelectionWorks_WithDateVariables() {
    // given
    final Map<String, Object> variables = Collections.singletonMap(
      "dateVar",
      OffsetDateTime.parse("2020-06-15T00:00:00+02:00")
    );
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcess(variables);
    final ProcessInstanceEngineDto procInst2 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      Collections.singletonMap(
        "dateVar",
        OffsetDateTime.parse("2020-06-20T00:00:00+02:00")
      )
    );
    final ProcessInstanceEngineDto procInst3 = engineIntegrationExtension.startProcessInstance(
      procInst1.getDefinitionId(),
      variables
    );

    final OffsetDateTime startOfToday = dateFreezer().freezeDateAndReturn().truncatedTo(ChronoUnit.DAYS);
    changeProcessInstanceDate(procInst1.getId(), startOfToday);
    changeProcessInstanceDate(procInst2.getId(), startOfToday);
    changeProcessInstanceDate(procInst3.getId(), startOfToday.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.DATE,
      "dateVar",
      AggregateByDateUnit.AUTOMATIC
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(result.getFirstMeasureData())
      .extracting(HyperMapResultEntryDto::getValue)
      .allSatisfy(
        resultEntries -> assertThat(resultEntries).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      );
    assertThat(result.getFirstMeasureData().stream()
                 .flatMap(e -> e.getValue().stream())
                 .mapToDouble(MapResultEntryDto::getValue)
                 .sum())
      .isEqualTo(3.0);
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() {
    // given
    final ProcessInstanceEngineDto procInst1 = deployAndStartSimpleProcessWithStringVariable();
    final ProcessInstanceEngineDto procInst2 = startInstanceWithStringVariable(procInst1.getDefinitionId());

    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    changeProcessInstanceDate(procInst1.getId(), referenceDate);
    changeProcessInstanceDate(procInst2.getId(), referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      procInst1,
      VariableType.STRING,
      "stringVar",
      AggregateByDateUnit.DAY
    );
    final ReportResultResponseDto<List<HyperMapResultEntryDto>>result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfToday.minusDays(2)), 1.0)
          .distributedByContains(localDateTimeToString(startOfToday.minusDays(1)), 0.0)
          .distributedByContains(localDateTimeToString(startOfToday), 1.0)
      .doAssert(result);
    // @formatter:on
  }

  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName) {
    return createReportData(processInstanceDto, variableType, variableName, AggregateByDateUnit.DAY);
  }

  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
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

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithStringVariable() {
    return deployAndStartSimpleProcess(Collections.singletonMap(
      "stringVar",
      "a string"
    ));
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

  private ProcessInstanceEngineDto startInstanceWithStringVariable(final String definitionId) {
    return engineIntegrationExtension.startProcessInstance(
      definitionId,
      Collections.singletonMap("stringVar", "a string")
    );
  }

}
