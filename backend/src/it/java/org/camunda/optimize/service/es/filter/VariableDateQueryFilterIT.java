/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableDateQueryFilterIT extends AbstractFilterIT {

  private static final String VARIABLE_NAME = "var";

  @BeforeEach
  public void setup() {
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2019-06-15T12:00:00+02:00"));
  }

  @Test
  public void dateLessThanOrEqualVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(1));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.plusSeconds(10));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(null, now)
        .add()
        .buildList();

    final RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
  }

  @Test
  public void dateGreaterOrEqualThanVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.plusSeconds(10));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(now, null)
        .add()
        .buildList();

    final RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
  }

  @Test
  public void dateEqualVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.plusSeconds(10));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(now, now)
        .add()
        .buildList();

    final RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void dateWithinRangeVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filter =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(now.minusSeconds(1), now.plusSeconds(10))
        .add()
        .buildList();

    final RawDataProcessReportResultDto result = evaluateReportWithFilter(processDefinition, filter);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void overlappingDateFiltersYieldConjunctInstancesVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.plusSeconds(10));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(now.minusSeconds(5), now)
        .add()
        .variable()
        .name(VARIABLE_NAME)
        .dateType()
        .fixedDate(now, now.plusSeconds(20))
        .add()
        .buildList();

    final RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void nonOverlappingDateFiltersYieldNoResultsVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusSeconds(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.plusSeconds(10));

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filters =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .fixedDate(now.minusSeconds(2), now.minusSeconds(1))
        .add()
        .variable()
        .name(VARIABLE_NAME)
        .dateType()
        .fixedDate(now.plusSeconds(1), now.plusSeconds(2))
        .add()
        .buildList();

    final RawDataProcessReportResultDto result =
      evaluateReportWithFilter(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters);

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
  }

  @Test
  public void relativeDateVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusDays(2));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now.minusDays(3));
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filters1 =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .relativeDate(1L, DateFilterUnit.DAYS)
        .add()
        .buildList();
    final RawDataProcessReportResultDto result1 = evaluateReportWithFilter(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters1
    );

    final List<ProcessFilterDto<?>> filters2 =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .relativeDate(3L, DateFilterUnit.DAYS)
        .add()
        .buildList();
    final RawDataProcessReportResultDto result2 = evaluateReportWithFilter(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filters2
    );

    // then
    assertThat(result1.getInstanceCount()).isEqualTo(1L);
    assertThat(result2.getInstanceCount()).isEqualTo(3L);
  }

  @Test
  public void rollingDateVariableFilter() {
    // given
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startInstanceForDefinitionWithDateVar(processDefinition.getId(), now);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto<?>> filterToday =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .rollingDate(0L, DateFilterUnit.DAYS)
        .add()
        .buildList();
    final RawDataProcessReportResultDto result1 = evaluateReportWithFilter(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filterToday
    );

    // now move the current day
    LocalDateUtil.setCurrentTime(now.plusDays(1L));
    final RawDataProcessReportResultDto result2 = evaluateReportWithFilter(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filterToday
    );

    final List<ProcessFilterDto<?>> filterPreviousDay =
      ProcessFilterBuilder
        .filter()
        .variable()
        .dateType()
        .name(VARIABLE_NAME)
        .rollingDate(1L, DateFilterUnit.DAYS)
        .add()
        .buildList();
    final RawDataProcessReportResultDto result3 = evaluateReportWithFilter(
      processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), filterPreviousDay
    );


    // then
    assertThat(result1.getInstanceCount()).isEqualTo(1L);
    assertThat(result2.getInstanceCount()).isEqualTo(0L);
    assertThat(result3.getInstanceCount()).isEqualTo(1L);
  }

  private void startInstanceForDefinitionWithDateVar(final String definitionId, final OffsetDateTime variableValue) {
    engineIntegrationExtension.startProcessInstance(definitionId, ImmutableMap.of(VARIABLE_NAME, variableValue));
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(final ProcessDefinitionEngineDto processDefinition,
                                                                 final List<ProcessFilterDto<?>> filter) {
    return evaluateReportWithFilter(processDefinition.getKey(), processDefinition.getVersionAsString(), filter);
  }

  private RawDataProcessReportResultDto evaluateReportWithFilter(String processDefinitionKey,
                                                                 String processDefinitionVersion,
                                                                 List<ProcessFilterDto<?>> filter) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(filter)
      .build();
    return evaluateReportAndReturnResult(reportData);
  }

}
