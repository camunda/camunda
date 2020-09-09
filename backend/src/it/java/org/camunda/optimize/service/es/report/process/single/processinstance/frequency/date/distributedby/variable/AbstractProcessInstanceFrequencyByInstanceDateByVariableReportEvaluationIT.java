/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.date.distributedby.variable;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
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
    ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(GroupByDateUnit.DAY);
    assertThat(resultReportDataDto.getConfiguration()
                 .getDistributedBy()
                 .getType()).isEqualTo(DistributedByType.VARIABLE);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit())
      .isEqualTo(GroupByDateUnit.DAY);
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy().getType())
      .isEqualTo(DistributedByType.VARIABLE);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
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
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
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
  public void customOrderOnResultValueIsApplied() {
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
    changeProcessInstanceDate(procInstance3.getId(), referenceDate.minusDays(1));
    final ProcessInstanceEngineDto procInstance4 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "this is also a string")
      );
    changeProcessInstanceDate(procInstance4.getId(), referenceDate.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(4L)
      .processInstanceCountWithoutFilters(4L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("a string", 1.0)
        .distributedByContains("another string", 0.0)
        .distributedByContains("this is also a string", 0.0)
      .groupByContains(localDateTimeToString(startOfToday.minusDays(1)))
        .distributedByContains("this is also a string", 2.0)
        .distributedByContains("another string", 1.0)
        .distributedByContains("a string", 0.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig_stringVariable() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

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
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .isComplete(false)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("a string", 1.0)
        .distributedByContains("another string", 0.0)
      .groupByContains(localDateTimeToString(startOfToday.minusDays(1)))
        .distributedByContains("a string", 0.0)
        .distributedByContains("another string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig_boolVariable() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("boolVar", true));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("boolVar", false)
      );
    changeProcessInstanceDate(procInstance2.getId(), referenceDate.minusDays(1));

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("boolVar", true)
      );
    changeProcessInstanceDate(procInstance3.getId(), referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.BOOLEAN, "boolVar");
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .isComplete(false)
      .groupByContains(localDateTimeToString(startOfToday))
      .distributedByContains("false", 0.0)
      .distributedByContains("true", 1.0)
      .groupByContains(localDateTimeToString(startOfToday.minusDays(1)))
      .distributedByContains("false", 1.0)
      .distributedByContains("true", 0.0)
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("a string", 1.0)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void dateVariable_returnsEmptyResult() {
    // given a report with a date variable (not yet supported)
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("dateVar", referenceDate));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DATE, "dateVar");
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then there are no distributed by results
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfToday))
      .doAssert(result);
    // @formatter:on

  }

  @Test
  public void numberVariable_returnsEmptyResult() {
    // given a report with a number variable (not yet supported)
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("numberVar", 1.0));
    changeProcessInstanceDate(procInstance1.getId(), referenceDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "numberVar");
    final String reportId = createNewReport(reportData);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then an empty result is returned
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfToday))
      .doAssert(result);
    // @formatter:on
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
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReportById(reportId).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(5L)
      .processInstanceCountWithoutFilters(5L)
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
      createReportData(procInstance, VariableType.BOOLEAN, "boolVar", GroupByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then a non-empty result is returned with instances in the first and last bucket
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getInstanceCount()).isEqualTo(2);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(result.getData()).isNotEmpty();

    assertThat(result.getData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .first()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.0);
    assertThat(result.getData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .last()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.0);
  }


  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName) {
    return createReportData(processInstanceDto, variableType, variableName, GroupByDateUnit.DAY);
  }

  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName,
                                                final GroupByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(groupByDateUnit)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
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

}