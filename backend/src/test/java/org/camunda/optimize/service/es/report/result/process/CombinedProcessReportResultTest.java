/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.process;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.export.CSVUtils;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CombinedProcessReportResultTest {

  @Test
  public void testGetResultAsCsvForMapResult() {
    // given
    final ReportMapResultDto mapResultDto = new ReportMapResultDto();
    final List<MapResultEntryDto> resultDtoMap = new ArrayList<>();
    resultDtoMap.add(new MapResultEntryDto("900.0", 1.));
    resultDtoMap.add(new MapResultEntryDto("10.99", 1.));
    mapResultDto.addMeasure(MeasureDto.of(ViewProperty.RAW_DATA, resultDtoMap));
    final List<SingleReportResultDto> mapResultDtos = Lists.newArrayList(
      mapResultDto,
      mapResultDto
    );

    CombinedProcessReportResult underTest = createTestCombinedProcessReportResult(
      ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE,
      mapResultDtos,
      AggregationType.AVERAGE
    );

    // when
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());


    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1))
      .isEqualTo(new String[]{
        "variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE", "processInstance_frequency"
      });
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[]{"10.99", "1.0", "", "10.99", "1.0"});
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[]{"900.0", "1.0", "", "900.0", "1.0"});

    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1)).isEqualTo(
      new String[]{"variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE",
        "processInstance_frequency"});
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[]{"10.99", "1.0", "", "10.99", "1.0"});

    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1)).isEqualTo(
      new String[]{"variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE",
        "processInstance_frequency"});
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[]{"900.0", "1.0", "", "900.0", "1.0"});
  }

  @Test
  public void testGetResultAsCsvForNumberResult() {

    // given
    final NumberResultDto numberResultDto1 = new NumberResultDto();
    numberResultDto1.addMeasure(MeasureDto.of(ViewProperty.FREQUENCY, 5.));

    final NumberResultDto numberResultDto2 = new NumberResultDto();
    numberResultDto2.addMeasure(MeasureDto.of(ViewProperty.FREQUENCY, 2.));

    final ArrayList<SingleReportResultDto> resultDtos = Lists.newArrayList(
      numberResultDto1,
      numberResultDto2
    );

    CombinedProcessReportResult underTest = createTestCombinedProcessReportResult(
      ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE,
      resultDtos,
      AggregationType.AVERAGE
    );

    // when
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "SingleTestReport1"});
    assertThat(resultAsCsv.get(1))
      .isEqualTo(new String[]{"processInstance_frequency", "", "processInstance_frequency"});
    assertThat(resultAsCsv.get(2)).isEqualTo(new String[]{"5.0", "", "2.0"});
  }

  @ParameterizedTest(name = "Test get result as CSV for duration number result with aggregate type {0}")
  @MethodSource("allAggregationTypesWithoutSum")
  public void testGetResultAsCsvForDurationNumberResult(final AggregationType aggregationType) {
    // given

    final NumberResultDto durReportDto = new NumberResultDto();
    durReportDto.addMeasure(MeasureDto.of(ViewProperty.DURATION, 6.));

    final ArrayList<SingleReportResultDto> resultDtos = Lists.newArrayList(
      durReportDto,
      durReportDto
    );

    CombinedProcessReportResult underTest = createTestCombinedProcessReportResult(
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE,
      resultDtos,
      aggregationType
    );

    // when
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertCsvByAggregationType(resultAsCsv, aggregationType);
  }

  private void assertCsvByAggregationType(final List<String[]> resultAsCsv, AggregationType aggregationType) {
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "SingleTestReport1"});
    assertThat(resultAsCsv.get(1)).isEqualTo(new String[]{"processInstance_duration", "", "processInstance_duration"});
    assertThat(resultAsCsv.get(2)).isEqualTo(
      new String[]{CSVUtils.mapAggregationType(aggregationType), "", CSVUtils.mapAggregationType(aggregationType)});

    assertThat(resultAsCsv.get(3)).isEqualTo(new String[]{"6.0", "", "6.0"});
  }

  @ParameterizedTest(name = "Test get result as CSV for duration map result with aggregate type {0}")
  @MethodSource("allAggregationTypesWithoutSum")
  public void testGetResultAsCsvForDurationMapResult(final AggregationType aggregationType) {
    // given

    final ReportMapResultDto durMapReportDto = new ReportMapResultDto();

    List<MapResultEntryDto> data = new ArrayList<>();
    data.add(new MapResultEntryDto("test1", 3.));
    data.add(new MapResultEntryDto("test2", 6.));
    durMapReportDto.addMeasure(MeasureDto.of(ViewProperty.DURATION, data));

    final ArrayList<SingleReportResultDto> resultDtos = Lists.newArrayList(
      durMapReportDto,
      durMapReportDto
    );

    CombinedProcessReportResult underTest = createTestCombinedProcessReportResult(
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE,
      resultDtos,
      aggregationType
    );

    // when
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1)).isEqualTo(new String[]{
      "variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
      "processInstance_duration"});
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)}).isEqualTo(resultAsCsv.get(2)
    );
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[]{"test1", "3.0", "", "test1", "3.0"});
    assertThat(resultAsCsv.get(4)).isEqualTo(new String[]{"test2", "6.0", "", "test2", "6.0"});

    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1)).isEqualTo(
      new String[]{"variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
        "processInstance_duration"});
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)}).isEqualTo(resultAsCsv.get(2)
    );
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[]{"test1", "3.0", "", "test1", "3.0"});


    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, ZoneId.systemDefault());


    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""});
    assertThat(resultAsCsv.get(1)).isEqualTo(
      new String[]{"variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
        "processInstance_duration"});
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)}).isEqualTo(resultAsCsv.get(2)
    );
    assertThat(resultAsCsv.get(3)).isEqualTo(new String[]{"test2", "6.0", "", "test2", "6.0"});
  }

  @Test
  public void testGetResultAsCsvForEmptyReport() {
    // given
    CombinedProcessReportResult underTest = new CombinedProcessReportResult(
      new CombinedProcessReportResultDto(new HashMap<String, ReportEvaluationResult<SingleReportResultDto,
        SingleProcessReportDefinitionRequestDto>>(), 0L),
      new CombinedReportDefinitionRequestDto()
    );

    // when
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, ZoneId.systemDefault());

    // then
    assertThat(resultAsCsv.get(0)).isEqualTo(new String[]{});
  }


  private CombinedProcessReportResult createTestCombinedProcessReportResult(ProcessReportDataType reportDataType,
                                                                            List<SingleReportResultDto> reportResultDtos,
                                                                            AggregationType aggregationType) {

    final ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setVariableName("test")
      .setReportDataType(reportDataType)
      .setVariableType(VariableType.DOUBLE)
      .build();
    processReportDataDto.getConfiguration().setAggregationTypes(aggregationType);

    List<ReportEvaluationResult> reportEvaluationResults = new ArrayList<>();

    for (int i = 0; i < reportResultDtos.size(); i++) {
      final SingleProcessReportDefinitionRequestDto singleDefDto = new SingleProcessReportDefinitionRequestDto();
      singleDefDto.setName("SingleTestReport" + i);
      singleDefDto.setData(processReportDataDto);

      reportEvaluationResults.add(createReportEvaluationResult(reportResultDtos.get(i), singleDefDto));
    }

    return createCombinedProcessReportResult(reportEvaluationResults);
  }

  private ReportEvaluationResult createReportEvaluationResult(final SingleReportResultDto reportResultDto,
                                                              final SingleProcessReportDefinitionRequestDto singleDefDto) {
    ReportEvaluationResult reportResult = null;

    final boolean isFrequencyReport = singleDefDto.getData().isFrequencyReport();
    if (reportResultDto instanceof ReportMapResultDto && isFrequencyReport) {
      reportResult = new SingleProcessMapReportResult((ReportMapResultDto) reportResultDto, singleDefDto);

    } else if (reportResultDto instanceof NumberResultDto && isFrequencyReport) {
      reportResult = new SingleProcessNumberReportResult((NumberResultDto) reportResultDto, singleDefDto);

    } else if (reportResultDto instanceof NumberResultDto) {
      reportResult = new SingleProcessNumberReportResult(
        (NumberResultDto) reportResultDto,
        singleDefDto
      );
    } else if (reportResultDto instanceof ReportMapResultDto) {
      reportResult = new SingleProcessMapReportResult(
        (ReportMapResultDto) reportResultDto,
        singleDefDto
      );
    }
    return reportResult;
  }

  private CombinedProcessReportResult createCombinedProcessReportResult(final List<ReportEvaluationResult> singleReportResults) {
    final LinkedHashMap<String, ReportEvaluationResult> mapIMap = new LinkedHashMap<>();

    for (int i = 0; i < singleReportResults.size(); i++) {
      mapIMap.put("test-id-" + i, singleReportResults.get(i));
    }

    return new CombinedProcessReportResult(
      new CombinedProcessReportResultDto(mapIMap, 0L),
      new CombinedReportDefinitionRequestDto()
    );
  }

  private static Stream<AggregationType> allAggregationTypesWithoutSum() {
    return AggregationType.getAggregationTypesAsListWithoutSum().stream();
  }

}
