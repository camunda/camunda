/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.camunda.optimize.service.es.report.result.process.SingleProcessRawDataReportResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_DECISION_REPORT_COLUMNS;
import static org.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS;

public class CSVUtilsTest {

  @Test
  public void testRawProcessResultMapping() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    //when
    List<String[]> result = mapRawProcessReportInstances(toMap);

    //then
    assertThat(result)
      .hasSize(4)
      .first().extracting(first -> first.length)
      .isEqualTo(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS);
  }

  @Test
  public void testRawProcessResultMapping_withExcludingField() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    final RawDataProcessReportResultDto rawDataProcessReportResultDto = new RawDataProcessReportResultDto();
    rawDataProcessReportResultDto.setData(toMap);

    List<String> excludedColumns = new ArrayList<>();
    excludedColumns.add(RawDataProcessInstanceDto.class.getDeclaredFields()[0].getName());
    final SingleProcessReportDefinitionDto reportDefinition = new SingleProcessReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleProcessRawDataReportResult rawDataReportResult =
      new SingleProcessRawDataReportResult(rawDataProcessReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
      .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawProcessResultMapping_withExcludingVariable() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    final RawDataProcessReportResultDto rawDataProcessReportResultDto = new RawDataProcessReportResultDto();
    rawDataProcessReportResultDto.setData(toMap);

    List<String> excludedColumns = new ArrayList<>();

    List<String> firstRowVariableColumnNames = Lists.newArrayList(toMap.get(0).getVariables().keySet());
    excludedColumns.add(CSVUtils.VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));
    final SingleProcessReportDefinitionDto reportDefinition = new SingleProcessReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleProcessRawDataReportResult rawDataReportResult =
      new SingleProcessRawDataReportResult(rawDataProcessReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
      .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMapping() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();

    //when
    List<String[]> result = mapRawDecisionReportInstances(toMap);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS);
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingField() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final RawDataDecisionReportResultDto rawDatadecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDatadecisionReportResultDto.setData(toMap);
    List<String> excludedColumns = new ArrayList<>();
    excludedColumns.add(RawDataDecisionInstanceDto.class.getDeclaredFields()[0].getName());
    excludedColumns.add(RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName());

    final SingleDecisionReportDefinitionDto reportDefinition = new SingleDecisionReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDatadecisionReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
      .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingInputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final RawDataDecisionReportResultDto rawDatadecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDatadecisionReportResultDto.setData(toMap);
    List<String> excludedColumns = new ArrayList<>();
    List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    excludedColumns.add(CSVUtils.INPUT_PREFIX + firstRowInputVariableColumnNames.get(0));

    final SingleDecisionReportDefinitionDto reportDefinition = new SingleDecisionReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDatadecisionReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
      .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingOutputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final RawDataDecisionReportResultDto rawDatadecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDatadecisionReportResultDto.setData(toMap);
    List<String> excludedColumns = new ArrayList<>();
    List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    excludedColumns.add(CSVUtils.OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

    final SingleDecisionReportDefinitionDto reportDefinition = new SingleDecisionReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDatadecisionReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
      .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingInputAndOutputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final RawDataDecisionReportResultDto rawDatadecisionReportResultDto = new RawDataDecisionReportResultDto();
    rawDatadecisionReportResultDto.setData(toMap);
    List<String> excludedColumns = new ArrayList<>();

    List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    excludedColumns.add(CSVUtils.INPUT_PREFIX + firstRowInputVariableColumnNames.get(0));

    List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    excludedColumns.add(CSVUtils.OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(2));

    final SingleDecisionReportDefinitionDto reportDefinition = new SingleDecisionReportDefinitionDto();
    reportDefinition.getData().getConfiguration().setExcludedColumns(excludedColumns);

    //when
    SingleDecisionRawDataReportResult rawDataReportResult =
      new SingleDecisionRawDataReportResult(rawDatadecisionReportResultDto, reportDefinition);
    List<String[]> result = rawDataReportResult.getResultAsCsv(10, null);

    //then
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
      .doesNotContainAnyElementsOf(excludedColumns);
  }

  private static List<String[]> mapRawProcessReportInstances(List<RawDataProcessInstanceDto> rawData) {
    return CSVUtils.mapRawProcessReportInstances(rawData, null, null, Collections.emptyList());
  }

  private static List<String[]> mapRawDecisionReportInstances(List<RawDataDecisionInstanceDto> rawData) {
    return CSVUtils.mapRawDecisionReportInstances(rawData, null, null, Collections.emptyList());
  }

}