/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static io.camunda.optimize.service.export.CSVUtils.mapCsvLinesToCsvBytes;
import static io.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_DECISION_REPORT_COLUMNS;
import static io.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataCountDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.service.db.report.result.RawDataCommandResult;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CSVUtilsTest {

  @Test
  public void
      testRawProcessResultMappingNewVariablesAndDtoFieldsAreExcludedWhenSetInReportConfig() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    final List<String> unexpectedVariableColumns =
        toMap.get(0).getVariables().keySet().stream()
            .map(varName -> VARIABLE_PREFIX + varName)
            .toList();
    final List<String> expectedDtoFieldColumns = extractAllProcessInstanceDtoFieldKeys();

    // when
    final List<String[]> result = mapRawProcessReportInstances(toMap, false);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(unexpectedVariableColumns)
        .containsAll(expectedDtoFieldColumns);
  }

  @Test
  public void testRawProcessResultMappingNewVariablesAndDtoFieldsAreIncludedWhenSet() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    final List<String> expectedVariableColumns =
        toMap.stream()
            .filter(instance -> instance.getVariables() != null)
            .flatMap(instance -> instance.getVariables().keySet().stream())
            .map(varName -> VARIABLE_PREFIX + varName)
            .toList();
    final List<String> expectedDtoFieldColumns = extractAllProcessInstanceDtoFieldKeys();

    // when
    final List<String[]> result = mapRawProcessReportInstances(toMap, true);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS + expectedVariableColumns.size())
        .containsAll(expectedVariableColumns)
        .containsAll(expectedDtoFieldColumns);
  }

  @Test
  public void testRawProcessResultMappingTestQuoteEscapingInValue() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("\"1\"", "test");
    final List<RawDataProcessInstanceDto> toMap =
        RawDataHelper.getRawDataProcessInstanceDtoWithVariables(variables);
    final String expectedString =
        "\"processDefinitionKey\",\"processDefinitionId\",\"processInstanceId\",\"startDate\","
            + "\"endDate\",\"duration\",\"engineName\",\"tenantId\","
            + "\"variable:\"\"1\"\"\"\r\n"
            + "\"test_key\",\"test_id\",,\"2018-02-23T14:31:08.048+01:00\",\"2018-02-23T14:31:08"
            + ".048+01:00\",\"0\",\"engine\",\"tenant\",\"test\"\r\n";

    // when
    final String resultString =
        new String(mapCsvLinesToCsvBytes(mapRawProcessReportInstances(toMap, true), ','));

    // then
    assertThat(resultString).isEqualTo(expectedString);
  }

  @ParameterizedTest
  @MethodSource("getExpectedStringAndCsvDelimiter")
  public void testRawProcessResultMappingCsvWorksWithSeveralDelimiters(
      final String expectedString, final char delimiter) {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("\"1\"", "test");
    final List<RawDataProcessInstanceDto> toMap =
        RawDataHelper.getRawDataProcessInstanceDtoWithVariables(variables);

    // when
    final String resultString =
        new String(mapCsvLinesToCsvBytes(mapRawProcessReportInstances(toMap, true), delimiter));

    // then
    assertThat(resultString).isEqualTo(expectedString);
  }

  @Test
  public void testRawProcessResultMappingWithIncludingAndExcludingFields() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    final List<String> excludedColumns =
        Lists.newArrayList(RawDataProcessInstanceDto.class.getDeclaredFields()[0].getName());

    final SingleProcessReportDefinitionRequestDto reportDefinition =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);
    // variables are irrelevant for this test case
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(
            extractAllProcessInstanceDtoFieldKeys().size()
                - excludedColumns.size()
                + RawDataCountDto.Fields.values().length)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawProcessResultMappingWithIncludingAndExcludingSameFieldExcludeWins() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    final List<String> includedColumns = extractAllProcessInstanceDtoFieldKeys();
    final List<String> excludedColumns =
        Lists.newArrayList(RawDataProcessInstanceDto.class.getDeclaredFields()[1].getName());
    final SingleProcessReportDefinitionRequestDto reportDefinition =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);
    // variables are irrelevant for this test case
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(
            includedColumns.size()
                - excludedColumns.size()
                + RawDataCountDto.Fields.values().length)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawProcessResultMappingWithExcludingVariables() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    final List<String> firstRowVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getVariables().keySet());
    final List<String> excludedColumns =
        Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));
    final SingleProcessReportDefinitionRequestDto reportDefinition =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault(), false);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawProcessResultMappingWithIncludingVariables() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    final List<String> firstRowVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getVariables().keySet());
    final List<String> includedColumns =
        Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));
    includedColumns.addAll(extractAllProcessInstanceDtoFieldKeys());
    final SingleProcessReportDefinitionRequestDto reportDefinition =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault(), false);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).hasSize(includedColumns.size());
  }

  @Test
  public void
      testRawProcessResultMappingWithIncludingVariableAndExcludingSameVariableExcludeWins() {
    // given
    final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    final List<String> firstRowVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getVariables().keySet());
    final List<String> includedColumns =
        Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(1));
    final List<String> excludedColumns =
        Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(1));
    final SingleProcessReportDefinitionRequestDto reportDefinition =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault(), false);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void
      testRawDecisionResultMappingNewVariablesAreExcludedAndDtoFieldsAreIncludedByDefault() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> unexpectedInputVariableColumns =
        toMap.get(0).getInputVariables().keySet().stream()
            .map(varName -> INPUT_PREFIX + varName)
            .toList();
    final List<String> unexpectedOutputVariableColumns =
        toMap.get(0).getOutputVariables().keySet().stream()
            .map(varName -> OUTPUT_PREFIX + varName)
            .toList();
    final List<String> expectedDtoFieldColumns = extractAllDecisionInstanceDtoFieldKeys();

    // when
    final List<String[]> result = mapRawDecisionReportInstances(toMap);

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
        .containsAll(expectedDtoFieldColumns)
        .doesNotContainAnyElementsOf(unexpectedInputVariableColumns)
        .doesNotContainAnyElementsOf(unexpectedOutputVariableColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithExcludingFields() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> excludedColumns =
        Lists.newArrayList(
            RawDataDecisionInstanceDto.class.getDeclaredFields()[0].getName(),
            RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName());

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);
    // variables are irrelevant for this test case
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(extractAllDecisionInstanceDtoFieldKeys().size() - excludedColumns.size())
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithIncludingFieldAndExcludingSameFieldExcludeWins() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();

    final List<String> includedColumns =
        Lists.newArrayList(RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName());
    final List<String> excludedColumns =
        Lists.newArrayList(RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName());
    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithExcludingInputVariables() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowInputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    final List<String> excludedColumns =
        Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithIncludingInputVariables() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowInputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    final List<String> includedColumns =
        Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(extractAllDecisionInstanceDtoFieldKeys().size() + includedColumns.size())
        .containsAll(includedColumns);
  }

  @Test
  public void
      testRawDecisionResultMappingWithIncludingInputVariableAndExcludingSameVariableExcludeWins() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowInputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    final List<String> includedColumns =
        Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));
    final List<String> excludedColumns =
        Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithExcludingOutputVariable() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowOutputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    final List<String> excludedColumns =
        Lists.newArrayList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  @Test
  public void testRawDecisionResultMappingWithIncludingOutputVariable() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowOutputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    final List<String> includedColumns =
        Lists.newArrayList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(extractAllDecisionInstanceDtoFieldKeys().size() + includedColumns.size())
        .containsAnyElementsOf(includedColumns);
  }

  @Test
  public void
      testRawDecisionResultMappingWithIncludingOutputVariableAndExcludingSameVariableExcludeWins() {
    // given
    final List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    final List<String> firstRowOutputVariableColumnNames =
        Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    final List<String> includedColumns =
        Collections.singletonList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(1));
    final List<String> excludedColumns =
        Collections.singletonList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(1));

    final SingleDecisionReportDefinitionRequestDto reportDefinition =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getIncludedColumns()
        .addAll(includedColumns);
    reportDefinition
        .getData()
        .getConfiguration()
        .getTableColumns()
        .getExcludedColumns()
        .addAll(excludedColumns);

    // when
    final RawDataCommandResult rawDataReportResult =
        new RawDataCommandResult(toMap, reportDefinition.getData());
    final List<String[]> result =
        rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

    // then
    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
        .doesNotContainAnyElementsOf(excludedColumns);
  }

  private static List<String[]> mapRawProcessReportInstances(
      final List<RawDataProcessInstanceDto> rawData, final boolean includeNewVariables) {
    return CSVUtils.mapRawProcessReportInstances(
        rawData, null, null, new TableColumnDto(), includeNewVariables);
  }

  private static List<String[]> mapRawDecisionReportInstances(
      final List<RawDataDecisionInstanceDto> rawData) {
    return CSVUtils.mapRawDecisionReportInstances(rawData, null, null, new TableColumnDto());
  }

  private static Stream<Arguments> getExpectedStringAndCsvDelimiter() {
    return Stream.of(
        Arguments.of(
            "\"processDefinitionKey\",\"processDefinitionId\",\"processInstanceId\",\"startDate\","
                + "\"endDate\",\"duration\",\"engineName\",\"tenantId\",\"variable:\"\"1\"\"\"\r\n"
                + "\"test_key\",\"test_id\",,\"2018-02-23T14:31:08.048+01:00\",\"2018-02-23T14:31:08"
                + ".048+01:00\",\"0\",\"engine\",\"tenant\",\"test\"\r\n",
            ','),
        Arguments.of(
            "\"processDefinitionKey\";\"processDefinitionId\";\"processInstanceId\";\"startDate\";"
                + "\"endDate\";\"duration\";\"engineName\";\"tenantId\";\"variable:\"\"1\"\"\"\r\n"
                + "\"test_key\";\"test_id\";;\"2018-02-23T14:31:08.048+01:00\";\"2018-02-23T14:31:08"
                + ".048+01:00\";\"0\";\"engine\";\"tenant\";\"test\"\r\n",
            ';'),
        Arguments.of(
            "\"processDefinitionKey\"\t\"processDefinitionId\"\t\"processInstanceId\"\t\"startDate\""
                + "\t\"endDate\"\t\"duration\"\t\"engineName\"\t\"tenantId\"\t\"variable:\"\"1\"\"\"\r\n"
                + "\"test_key\"\t\"test_id\"\t\t\"2018-02-23T14:31:08.048+01:00\"\t"
                + "\"2018-02-23T14:31:08.048+01:00\"\t\"0\"\t\"engine\"\t\"tenant\"\t\"test\"\r\n",
            '\t'),
        Arguments.of(
            "\"processDefinitionKey\"|\"processDefinitionId\"|\"processInstanceId\"|\"startDate\"|"
                + "\"endDate\"|\"duration\"|\"engineName\"|\"tenantId\"|\"variable:\"\"1\"\"\"\r\n"
                + "\"test_key\"|\"test_id\"||\"2018-02-23T14:31:08.048+01:00\"|\"2018-02-23T14:31:08"
                + ".048+01:00\"|\"0\"|\"engine\"|\"tenant\"|\"test\"\r\n",
            '|'),
        Arguments.of(
            "\"processDefinitionKey\" \"processDefinitionId\" \"processInstanceId\" \"startDate\" "
                + "\"endDate\" \"duration\" \"engineName\" \"tenantId\" \"variable:\"\"1\"\"\"\r\n"
                + "\"test_key\" \"test_id\"  \"2018-02-23T14:31:08.048+01:00\" \"2018-02-23T14:31:08"
                + ".048+01:00\" \"0\" \"engine\" \"tenant\" \"test\"\r\n",
            ' '));
  }
}
