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
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.export.CSVUtils;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class CombinedProcessReportResultTest {

  @Test
  public void testGetResultAsCsvForMapResult() {
    // given
    final ReportMapResultDto mapResultDto = new ReportMapResultDto();
    final List<MapResultEntryDto> resultDtoMap = new ArrayList<>();
    resultDtoMap.add(new MapResultEntryDto("900.0", 1L));
    resultDtoMap.add(new MapResultEntryDto("10.99", 1L));
    mapResultDto.setData(resultDtoMap);
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
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, null);


    // then
    assertThat(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""}, is(resultAsCsv.get(0)));
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE",
        "processInstance_frequency"},
      is(resultAsCsv.get(1))
    );
    assertThat(new String[]{"900.0", "1", "", "900.0", "1"}, is(resultAsCsv.get(2)));
    assertThat(new String[]{"10.99", "1", "", "10.99", "1"}, is(resultAsCsv.get(3)));


    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, null);


    // then
    assertThat(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""}, is(resultAsCsv.get(0)));
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE",
        "processInstance_frequency"},
      is(resultAsCsv.get(1))
    );
    assertThat(new String[]{"900.0", "1", "", "900.0", "1"}, is(resultAsCsv.get(2)));


    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, null);


    // then
    assertThat(new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""}, is(resultAsCsv.get(0)));
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_frequency", "", "variable_test_DOUBLE",
        "processInstance_frequency"},
      is(resultAsCsv.get(1))
    );
    assertThat(new String[]{"10.99", "1", "", "10.99", "1"}, is(resultAsCsv.get(2)));
  }

  @Test
  public void testGetResultAsCsvForNumberResult() {

    // given
    final NumberResultDto numberResultDto1 = new NumberResultDto();
    numberResultDto1.setData(5l);

    final NumberResultDto numberResultDto2 = new NumberResultDto();
    numberResultDto2.setData(2l);

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
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, null);


    // then
    assertThat(
      new String[]{"SingleTestReport0", "", "SingleTestReport1"}, is(resultAsCsv.get(0))
    );
    assertThat(
      new String[]{"processInstance_frequency", "", "processInstance_frequency"},
      is(resultAsCsv.get(1))
    );
    assertThat(
      new String[]{"5", "", "2"}, is(resultAsCsv.get(2))
    );
  }

  @ParameterizedTest(name = "Test get result as CSV for duration number result with aggregate type {0}")
  @EnumSource(AggregationType.class)
  public void testGetResultAsCsvForDurationNumberResult(final AggregationType aggregationType) {
    // given

    final NumberResultDto durReportDto = new NumberResultDto();
    durReportDto.setData(6L);

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
    final List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, null);

    // then
    assertCsvByAggregationType(resultAsCsv, aggregationType);
  }

  private void assertCsvByAggregationType(final List<String[]> resultAsCsv, AggregationType aggregationType) {
    assertThat(
      new String[]{"SingleTestReport0", "", "SingleTestReport1"},
      is(resultAsCsv.get(0))
    );
    assertThat(
      new String[]{"processInstance_duration", "", "processInstance_duration"},
      is(resultAsCsv.get(1))
    );
    assertThat(
      new String[]{CSVUtils.mapAggregationType(aggregationType), "", CSVUtils.mapAggregationType(aggregationType)},
      is(resultAsCsv.get(2))
    );

    assertThat(new String[]{"6", "", "6"}, is(resultAsCsv.get(3)));
  }

  @ParameterizedTest(name = "Test get result as CSV for duration map result with aggregate type {0}")
  @EnumSource(AggregationType.class)
  public void testGetResultAsCsvForDurationMapResult(final AggregationType aggregationType) {
    // given

    final ReportMapResultDto durMapReportDto = new ReportMapResultDto();

    List<MapResultEntryDto> data = new ArrayList<>();
    data.add(new MapResultEntryDto("test1", 3L));
    data.add(new MapResultEntryDto("test2", 6L));
    durMapReportDto.setData(data);

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
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, null);

    // then
    assertThat(
      new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""},
      is(resultAsCsv.get(0))
    );
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
        "processInstance_duration"},
      is(resultAsCsv.get(1))
    );
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)},
      is(resultAsCsv.get(2))
    );
    assertThat(new String[]{"test1", "3", "", "test1", "3"}, is(resultAsCsv.get(3)));
    assertThat(new String[]{"test2", "6", "", "test2", "6"}, is(resultAsCsv.get(4)));

    // when (limit = 0)
    resultAsCsv = underTest.getResultAsCsv(0, 0, null);


    //then
    assertThat(
      new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""},
      is(resultAsCsv.get(0))
    );
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
        "processInstance_duration"},
      is(resultAsCsv.get(1))
    );
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)},
      is(resultAsCsv.get(2))
    );
    assertThat(new String[]{"test1", "3", "", "test1", "3"}, is(resultAsCsv.get(3)));


    // when (offset = 1)
    resultAsCsv = underTest.getResultAsCsv(0, 1, null);


    //then
    assertThat(
      new String[]{"SingleTestReport0", "", "", "SingleTestReport1", ""},
      is(resultAsCsv.get(0))
    );
    assertThat(
      new String[]{"variable_test_DOUBLE", "processInstance_duration", "", "variable_test_DOUBLE",
        "processInstance_duration"},
      is(resultAsCsv.get(1))
    );
    assertThat(
      new String[]{"", CSVUtils.mapAggregationType(aggregationType), "", "",
        CSVUtils.mapAggregationType(aggregationType)},
      is(resultAsCsv.get(2))
    );
    assertThat(new String[]{"test2", "6", "", "test2", "6"}, is(resultAsCsv.get(3)));
  }

  @Test
  public void testGetResultAsCsvForEmptyReport() {
    // given
    CombinedProcessReportResult underTest = new CombinedProcessReportResult(
      new CombinedProcessReportResultDto(new HashMap<String, ReportEvaluationResult<SingleReportResultDto,
        SingleProcessReportDefinitionDto>>()),
      new CombinedReportDefinitionDto()
    );

    // when
    List<String[]> resultAsCsv = underTest.getResultAsCsv(10, 0, null);

    // then
    assertThat(new String[]{}, is((resultAsCsv.get(0))));
  }


  private CombinedProcessReportResult createTestCombinedProcessReportResult(ProcessReportDataType reportDataType,
                                                                            List<SingleReportResultDto> reportResultDtos,
                                                                            AggregationType aggregationType) {

    final ProcessReportDataDto processReportDataDto = ProcessReportDataBuilder.createReportData()
      .setVariableName("test")
      .setReportDataType(reportDataType)
      .setVariableType(VariableType.DOUBLE)
      .build();
    processReportDataDto.getConfiguration().setAggregationType(aggregationType);

    List<ReportEvaluationResult> reportEvaluationResults = new ArrayList<>();

    for (int i = 0; i < reportResultDtos.size(); i++) {
      final SingleProcessReportDefinitionDto singleDefDto = new SingleProcessReportDefinitionDto();
      singleDefDto.setName("SingleTestReport" + i);
      singleDefDto.setData(processReportDataDto);

      reportEvaluationResults.add(createReportEvaluationResult(reportResultDtos.get(i), singleDefDto));
    }

    return createCombinedProcessReportResult(reportEvaluationResults);
  }

  private ReportEvaluationResult createReportEvaluationResult(final SingleReportResultDto reportResultDto,
                                                              final SingleProcessReportDefinitionDto singleDefDto) {
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
      new CombinedProcessReportResultDto(mapIMap),
      new CombinedReportDefinitionDto()
    );
  }


}