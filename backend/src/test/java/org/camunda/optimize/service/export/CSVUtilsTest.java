/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.service.es.report.result.RawDataCommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static org.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static org.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;
import static org.camunda.optimize.service.export.CSVUtils.mapCsvLinesToCsvBytes;
import static org.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_DECISION_REPORT_COLUMNS;
import static org.camunda.optimize.service.export.RawDataHelper.NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS;

public class CSVUtilsTest {

	@Test
	public void testRawProcessResultMapping_newVariablesAndDtoFieldsAreIncludedByDefault() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
		List<String> expectedVariableColumns = toMap.get(0).getVariables().keySet()
			.stream()
			.map(varName -> VARIABLE_PREFIX + varName)
			.collect(toList());
		List<String> expectedDtoFieldColumns = extractAllProcessInstanceDtoFieldKeys();

		// when
		List<String[]> result = mapRawProcessReportInstances(toMap);

		// then
		assertThat(result)
			.hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS)
			.containsAll(expectedVariableColumns)
			.containsAll(expectedDtoFieldColumns);
	}

	@Test
	public void testRawProcessResultMapping_testQuoteEscapingInValue() {
		// given
		final Map<String, Object> variables = new HashMap<>();
		variables.put("\"1\"",  "test");
		final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtoWithVariables(variables);
		final String expectedString =
			"\"processDefinitionKey\",\"processDefinitionId\",\"processInstanceId\",\"businessKey\",\"startDate\"," +
				"\"endDate\",\"duration\",\"engineName\",\"tenantId\",\"variable:\"\"1\"\"\"\r\n" +
				"\"test_key\",\"test_id\",,\"aBusinessKey\",\"2018-02-23T14:31:08.048+01:00\",\"2018-02-23T14:31:08" +
				".048+01:00\",\"0\",\"engine\",\"tenant\",\"test\"\r\n";

		// when
		final String resultString = new String(mapCsvLinesToCsvBytes(mapRawProcessReportInstances(toMap), ','));

		// then matches expected String
		assertThat(resultString).isEqualTo(expectedString);
	}

	@ParameterizedTest
	@MethodSource("getExpectedStringAndCsvDelimiter")
	public void testRawProcessResultMapping_csvWorksWithSeveralDelimters(String expectedString, char delimiter) {
		// given
		final Map<String, Object> variables = new HashMap<>();
		variables.put("\"1\"", "test");
		final List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtoWithVariables(variables);

		// when
		final String resultString = new String(mapCsvLinesToCsvBytes(mapRawProcessReportInstances(toMap), delimiter));

		// then matches expected String
		assertThat(resultString).isEqualTo(expectedString);
	}

	@Test
	public void testRawProcessResultMapping_withIncludingAndExcludingFields() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

		List<String> excludedColumns =
      Lists.newArrayList(RawDataProcessInstanceDto.class.getDeclaredFields()[0].getName());

		final SingleProcessReportDefinitionRequestDto reportDefinition = new SingleProcessReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);
		// variables are irrelevant for this test case
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(extractAllProcessInstanceDtoFieldKeys().size() - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawProcessResultMapping_withIncludingAndExcludingSameFieldExcludeWins() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

		List<String> includedColumns = extractAllProcessInstanceDtoFieldKeys();
		List<String> excludedColumns = Lists.newArrayList(
			RawDataProcessInstanceDto.class.getDeclaredFields()[1].getName()
		);
		final SingleProcessReportDefinitionRequestDto reportDefinition = new SingleProcessReportDefinitionRequestDto();
		reportDefinition.getData()
			.getConfiguration()
			.getTableColumns()
			.getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);
		// variables are irrelevant for this test case
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(includedColumns.size() - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawProcessResultMapping_withExcludingVariables() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

		List<String> firstRowVariableColumnNames = Lists.newArrayList(toMap.get(0).getVariables().keySet());
		List<String> excludedColumns = Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));
		final SingleProcessReportDefinitionRequestDto reportDefinition = new SingleProcessReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawProcessResultMapping_withIncludingVariables() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

		List<String> firstRowVariableColumnNames = Lists.newArrayList(toMap.get(0).getVariables().keySet());
		List<String> includedColumns = Lists.newArrayList(VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));
		includedColumns.addAll(extractAllProcessInstanceDtoFieldKeys());
		final SingleProcessReportDefinitionRequestDto reportDefinition = new SingleProcessReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(includedColumns.size());
	}

	@Test
	public void testRawProcessResultMapping_withIncludingVariableAndExcludingSameVariableExcludeWins() {
		// given
		List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

		List<String> firstRowVariableColumnNames = Lists.newArrayList(toMap.get(0).getVariables().keySet());
		List<String> includedColumns = Lists.newArrayList(
			VARIABLE_PREFIX + firstRowVariableColumnNames.get(1)
		);
		List<String> excludedColumns = Lists.newArrayList(
			VARIABLE_PREFIX + firstRowVariableColumnNames.get(1)
		);
		final SingleProcessReportDefinitionRequestDto reportDefinition = new SingleProcessReportDefinitionRequestDto();
		reportDefinition.getData()
			.getConfiguration()
			.getTableColumns()
			.getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_newVariablesAndDtoFieldsAreIncludedByDefault() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> expectedInputVariableColumns = toMap.get(0).getInputVariables().keySet()
			.stream()
			.map(varName -> INPUT_PREFIX + varName)
			.collect(toList());
		List<String> expectedOutputVariableColumns = toMap.get(0).getOutputVariables().keySet()
			.stream()
			.map(varName -> OUTPUT_PREFIX + varName)
			.collect(toList());
		List<String> expectedDtoFieldColumns = extractAllDecisionInstanceDtoFieldKeys();

		// when
		List<String[]> result = mapRawDecisionReportInstances(toMap);

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS)
			.containsAll(expectedDtoFieldColumns)
			.containsAll(expectedInputVariableColumns)
			.containsAll(expectedOutputVariableColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withExcludingFields() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> excludedColumns = Lists.newArrayList(
			RawDataDecisionInstanceDto.class.getDeclaredFields()[0].getName(),
			RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName()
		);

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);
		// variables are irrelevant for this test case
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(extractAllDecisionInstanceDtoFieldKeys().size() - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withIncludingFieldAndExcludingSameFieldExcludeWins() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();

		List<String> includedColumns = Lists.newArrayList(
			RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName()
		);
		List<String> excludedColumns = Lists.newArrayList(
			RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName()
		);
		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData()
			.getConfiguration()
			.getTableColumns()
			.getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withExcludingInputVariables() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
		List<String> excludedColumns = Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withIncludingInputVariables() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
		List<String> includedColumns = Lists.newArrayList(INPUT_PREFIX + firstRowInputVariableColumnNames.get(1));

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(extractAllDecisionInstanceDtoFieldKeys().size() + includedColumns.size())
			.containsAll(includedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withIncludingInputVariableAndExcludingSameVariableExcludeWins() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
		List<String> includedColumns = Lists.newArrayList(
			INPUT_PREFIX + firstRowInputVariableColumnNames.get(1)
		);
		List<String> excludedColumns = Lists.newArrayList(
			INPUT_PREFIX + firstRowInputVariableColumnNames.get(1)
		);

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withExcludingOutputVariable() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
		List<String> excludedColumns =
			Lists.newArrayList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withIncludingOutputVariable() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
		List<String> includedColumns =
			Lists.newArrayList(OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().setIncludeNewVariables(false);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(extractAllDecisionInstanceDtoFieldKeys().size() + includedColumns.size())
			.containsAnyElementsOf(includedColumns);
	}

	@Test
	public void testRawDecisionResultMapping_withIncludingOutputVariableAndExcludingSameVariableExcludeWins() {
		// given
		List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
		List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
		List<String> includedColumns = Collections.singletonList(
			OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(1)
		);
		List<String> excludedColumns = Collections.singletonList(
			OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(1)
		);

		final SingleDecisionReportDefinitionRequestDto reportDefinition = new SingleDecisionReportDefinitionRequestDto();
		reportDefinition.getData().getConfiguration().getTableColumns().getIncludedColumns().addAll(includedColumns);
		reportDefinition.getData().getConfiguration().getTableColumns().getExcludedColumns().addAll(excludedColumns);

		// when
		RawDataCommandResult rawDataReportResult =
			new RawDataCommandResult(toMap, reportDefinition.getData());
		List<String[]> result = rawDataReportResult.getResultAsCsv(10, null, ZoneId.systemDefault());

		// then
		assertThat(result).hasSize(4);
		assertThat(result.get(0))
			.hasSize(NUMBER_OF_RAW_DECISION_REPORT_COLUMNS - excludedColumns.size())
			.doesNotContainAnyElementsOf(excludedColumns);
	}

	private static List<String[]> mapRawProcessReportInstances(List<RawDataProcessInstanceDto> rawData) {
		return CSVUtils.mapRawProcessReportInstances(
			rawData,
			null,
			null,
			new TableColumnDto()
		);
	}

	private static List<String[]> mapRawDecisionReportInstances(List<RawDataDecisionInstanceDto> rawData) {
		return CSVUtils.mapRawDecisionReportInstances(
			rawData,
			null,
			null,
			new TableColumnDto()
		);
	}

	private static Stream<Arguments> getExpectedStringAndCsvDelimiter() {
		return Stream.of(
			Arguments.of(
				"\"processDefinitionKey\",\"processDefinitionId\",\"processInstanceId\",\"businessKey\",\"startDate\"," +
					"\"endDate\",\"duration\",\"engineName\",\"tenantId\",\"variable:\"\"1\"\"\"\r\n" +
					"\"test_key\",\"test_id\",,\"aBusinessKey\",\"2018-02-23T14:31:08.048+01:00\",\"2018-02-23T14:31:08" +
					".048+01:00\",\"0\",\"engine\",\"tenant\",\"test\"\r\n",
				','
			),
			Arguments.of(
				"\"processDefinitionKey\";\"processDefinitionId\";\"processInstanceId\";\"businessKey\";\"startDate\";" +
					"\"endDate\";\"duration\";\"engineName\";\"tenantId\";\"variable:\"\"1\"\"\"\r\n" +
					"\"test_key\";\"test_id\";;\"aBusinessKey\";\"2018-02-23T14:31:08.048+01:00\";\"2018-02-23T14:31:08" +
					".048+01:00\";\"0\";\"engine\";\"tenant\";\"test\"\r\n",
				';'
			),
			Arguments.of(
				"\"processDefinitionKey\"	\"processDefinitionId\"	\"processInstanceId\"	\"businessKey\"	\"startDate\"	" +
					"\"endDate\"	\"duration\"	\"engineName\"	\"tenantId\"	\"variable:\"\"1\"\"\"\r\n" +
					"\"test_key\"	\"test_id\"		\"aBusinessKey\"	\"2018-02-23T14:31:08.048+01:00\"	\"2018-02-23T14:31:08" +
					".048+01:00\"	\"0\"	\"engine\"	\"tenant\"	\"test\"\r\n",
				'\t'
			),
			Arguments.of(
				"\"processDefinitionKey\"|\"processDefinitionId\"|\"processInstanceId\"|\"businessKey\"|\"startDate\"|" +
					"\"endDate\"|\"duration\"|\"engineName\"|\"tenantId\"|\"variable:\"\"1\"\"\"\r\n" +
					"\"test_key\"|\"test_id\"||\"aBusinessKey\"|\"2018-02-23T14:31:08.048+01:00\"|\"2018-02-23T14:31:08" +
					".048+01:00\"|\"0\"|\"engine\"|\"tenant\"|\"test\"\r\n",
				'|'
			),
			Arguments.of(
				"\"processDefinitionKey\" \"processDefinitionId\" \"processInstanceId\" \"businessKey\" \"startDate\" " +
					"\"endDate\" \"duration\" \"engineName\" \"tenantId\" \"variable:\"\"1\"\"\"\r\n" +
					"\"test_key\" \"test_id\"  \"aBusinessKey\" \"2018-02-23T14:31:08.048+01:00\" \"2018-02-23T14:31:08" +
					".048+01:00\" \"0\" \"engine\" \"tenant\" \"test\"\r\n",
				' '
			)
		);
	}

}