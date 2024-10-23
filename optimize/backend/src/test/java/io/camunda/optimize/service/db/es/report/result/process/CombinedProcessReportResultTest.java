/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.result.process;

import static io.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.query.report.CombinedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.report.result.MapCommandResult;
import io.camunda.optimize.service.db.report.result.NumberCommandResult;
import io.camunda.optimize.service.export.CSVUtils;
import io.camunda.optimize.service.util.ProcessReportDataType;
import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class CombinedProcessReportResultTest {

  private static AggregationDto[] getAggregationTypes() {
    return getSupportedAggregationTypes();
  }

  @Test
  public void testGetResultAsCsvForMapResult() {
    // given
    final ProcessReportDataDto processReportDataDto =
        createProcessReportDataForType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE);
    final List<MapResultEntryDto> resultDtoMap = new ArrayList<>();
    resultDtoMap.add(new MapResultEntryDto("900.0", 1.));
    resultDtoMap.add(new MapResultEntryDto("10.99", 1.));
    final List<MapCommandResult> mapResults =
        Lists.newArrayList(
            new MapCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), resultDtoMap)),
                processReportDataDto),
            new MapCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), resultDtoMap)),
                processReportDataDto));

    // when
    final CombinedReportEvaluationResult underTest =
        createTestCombinedProcessReportResult(mapResults);
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_frequency",
              "",
              "variable_test_DOUBLE",
              "processInstance_frequency"
            });
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[] {"10.99", "1.0", "", "10.99", "1.0"});
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[] {"900.0", "1.0", "", "900.0", "1.0"});

    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_frequency",
              "",
              "variable_test_DOUBLE",
              "processInstance_frequency"
            });
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[] {"10.99", "1.0", "", "10.99", "1.0"});

    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_frequency",
              "",
              "variable_test_DOUBLE",
              "processInstance_frequency"
            });
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[] {"900.0", "1.0", "", "900.0", "1.0"});
  }

  @Test
  public void testGetResultAsCsvForNumberResult() {
    // given
    final ProcessReportDataDto processReportDataDto =
        createProcessReportDataForType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE);
    final List<NumberCommandResult> numberResults =
        Lists.newArrayList(
            new NumberCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), 5.)),
                processReportDataDto),
            new NumberCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), 2.)),
                processReportDataDto));

    // when
    final CombinedReportEvaluationResult underTest =
        createTestCombinedProcessReportResult(numberResults);
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "SingleTestReport1"});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(new String[] {"processInstance_frequency", "", "processInstance_frequency"});
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[] {"5.0", "", "2.0"});
  }

  @ParameterizedTest
  @MethodSource("getAggregationTypes")
  public void testGetResultAsCsvForDurationNumberResult(final AggregationDto aggregationType) {
    // given
    final ProcessReportDataDto processReportDataDto =
        createProcessReportDataForType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE);
    processReportDataDto.getConfiguration().setAggregationTypes(aggregationType);
    final List<NumberCommandResult> numberResults =
        List.of(
            new NumberCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), 6.)),
                processReportDataDto),
            new NumberCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), 6.)),
                processReportDataDto));

    // when
    final CombinedReportEvaluationResult underTest =
        createTestCombinedProcessReportResult(numberResults);
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertCsvByAggregationType(resultAsCsv, aggregationType);
  }

  private void assertCsvByAggregationType(
      final List<String[]> resultAsCsv, final AggregationDto aggregationDto) {
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "SingleTestReport1"});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(new String[] {"processInstance_duration", "", "processInstance_duration"});
    assertThat(resultAsCsv.get(2))
        .isEqualTo(
            new String[] {
              CSVUtils.mapAggregationType(aggregationDto),
              "",
              CSVUtils.mapAggregationType(aggregationDto)
            });

    assertThat(resultAsCsv.get(3)).isEqualTo(new String[] {"6.0", "", "6.0"});
  }

  @ParameterizedTest
  @MethodSource("getAggregationTypes")
  public void testGetResultAsCsvForDurationMapResult(final AggregationDto aggregationType) {
    // given
    final ProcessReportDataDto processReportDataDto =
        createProcessReportDataForType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE);
    processReportDataDto.getConfiguration().setAggregationTypes(aggregationType);
    final List<MapResultEntryDto> resultDtoMap = new ArrayList<>();
    resultDtoMap.add(new MapResultEntryDto("test1", 3.));
    resultDtoMap.add(new MapResultEntryDto("test2", 6.));
    final List<MapCommandResult> mapResults =
        Lists.newArrayList(
            new MapCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), resultDtoMap)),
                processReportDataDto),
            new MapCommandResult(
                Collections.singletonList(
                    MeasureDto.of(processReportDataDto.getViewProperties().get(0), resultDtoMap)),
                processReportDataDto));

    // when
    final CombinedReportEvaluationResult underTest =
        createTestCombinedProcessReportResult(mapResults);
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_duration",
              "",
              "variable_test_DOUBLE",
              "processInstance_duration"
            });
    assertThat(
            new String[] {
              "",
              CSVUtils.mapAggregationType(aggregationType),
              "",
              "",
              CSVUtils.mapAggregationType(aggregationType)
            })
        .isEqualTo(resultAsCsv.get(2));
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[] {"test1", "3.0", "", "test1", "3.0"});
    assertThat(resultAsCsv.get(4)).isEqualTo(new String[] {"test2", "6.0", "", "test2", "6.0"});

    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_duration",
              "",
              "variable_test_DOUBLE",
              "processInstance_duration"
            });
    assertThat(
            new String[] {
              "",
              CSVUtils.mapAggregationType(aggregationType),
              "",
              "",
              CSVUtils.mapAggregationType(aggregationType)
            })
        .isEqualTo(resultAsCsv.get(2));
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[] {"test1", "3.0", "", "test1", "3.0"});

    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0))
        .isEqualTo(new String[] {"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
        .isEqualTo(
            new String[] {
              "variable_test_DOUBLE",
              "processInstance_duration",
              "",
              "variable_test_DOUBLE",
              "processInstance_duration"
            });
    assertThat(
            new String[] {
              "",
              CSVUtils.mapAggregationType(aggregationType),
              "",
              "",
              CSVUtils.mapAggregationType(aggregationType)
            })
        .isEqualTo(resultAsCsv.get(2));
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[] {"test2", "6.0", "", "test2", "6.0"});
  }

  @Test
  public void testGetResultAsCsvForEmptyReport() {
    // given
    final CombinedReportEvaluationResult underTest =
        new CombinedReportEvaluationResult(
            Collections.emptyList(), 0L, new CombinedReportDefinitionRequestDto());

    // when
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[] {});
  }

  private <T> CombinedReportEvaluationResult createTestCombinedProcessReportResult(
      final List<? extends CommandEvaluationResult<T>> reportCommandResults) {

    final List<SingleReportEvaluationResult<?>> reportEvaluationResults = new ArrayList<>();
    for (int i = 0; i < reportCommandResults.size(); i++) {
      final CommandEvaluationResult<T> commandEvaluationResult = reportCommandResults.get(i);
      final ReportDefinitionDto<ProcessReportDataDto> reportDefinition =
          new SingleProcessReportDefinitionRequestDto();
      reportDefinition.setName("SingleTestReport" + i);
      reportDefinition.setData(commandEvaluationResult.getReportDataAs(ProcessReportDataDto.class));
      reportEvaluationResults.add(
          new SingleReportEvaluationResult<>(
              reportDefinition, Collections.singletonList(commandEvaluationResult)));
    }

    return createCombinedProcessReportResult(reportEvaluationResults);
  }

  private ProcessReportDataDto createProcessReportDataForType(
      final ProcessReportDataType reportDataType) {
    return TemplatedProcessReportDataBuilder.createReportData()
        .setVariableName("test")
        .setReportDataType(reportDataType)
        .setVariableType(VariableType.DOUBLE)
        .build();
  }

  private CombinedReportEvaluationResult createCombinedProcessReportResult(
      final List<SingleReportEvaluationResult<?>> singleReportResults) {
    return new CombinedReportEvaluationResult(
        singleReportResults, 0L, new CombinedReportDefinitionRequestDto());
  }
}
