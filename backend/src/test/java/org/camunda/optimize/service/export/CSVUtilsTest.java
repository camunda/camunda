/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;


public class CSVUtilsTest {

  @Test
  public void testRawProcessResultMapping() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    //when
    List<String[]> result = CSVUtils.mapRawProcessReportInstances(toMap);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(10));
  }


  @Test
  public void testRawProcessResultMapping_withExcludingField() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    excludedColumns.add(RawDataProcessInstanceDto.class.getDeclaredFields()[0].getName());

    //when
    List<String[]> result = CSVUtils.mapRawProcessReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(10 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testRawProcessResultMapping_withExcludingVariable() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();

    List<String> firstRowVariableColumnNames = Lists.newArrayList(toMap.get(0).getVariables().keySet());
    excludedColumns.add(CSVUtils.VARIABLE_PREFIX + firstRowVariableColumnNames.get(0));

    //when
    List<String[]> result = CSVUtils.mapRawProcessReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(10 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testRawDecisionResultMapping() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();

    //when
    List<String[]> result = CSVUtils.mapRawDecisionReportInstances(toMap);

    //then
    assertThat(result.size(), is(4));
    // 6 fields, 7 inputs, 14 outputs
    assertThat(result.get(0).length, is(6 + 7 + 14));
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingField() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    excludedColumns.add(RawDataDecisionInstanceDto.class.getDeclaredFields()[0].getName());
    excludedColumns.add(RawDataDecisionInstanceDto.class.getDeclaredFields()[1].getName());

    //when
    List<String[]> result = CSVUtils.mapRawDecisionReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    // 6 fields, 7 inputs, 14 outputs - 1 excluded fields
    assertThat(result.get(0).length, is(6 + 7 + 14 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingInputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    excludedColumns.add(CSVUtils.INPUT_PREFIX + firstRowInputVariableColumnNames.get(0));

    //when
    List<String[]> result = CSVUtils.mapRawDecisionReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    // 6 fields, 7 inputs, 14 outputs - 1 excluded fields
    assertThat(result.get(0).length, is(6 + 7 + 14 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingOutputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    excludedColumns.add(CSVUtils.OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(0));

    //when
    List<String[]> result = CSVUtils.mapRawDecisionReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    // 6 fields, 7 inputs, 14 outputs - 1 excluded fields
    assertThat(result.get(0).length, is(6 + 7 + 14 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testRawDecisionResultMapping_withExcludingInputAndOutputVariable() {
    //given
    List<RawDataDecisionInstanceDto> toMap = RawDataHelper.getRawDataDecisionInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();

    List<String> firstRowInputVariableColumnNames = Lists.newArrayList(toMap.get(0).getInputVariables().keySet());
    excludedColumns.add(CSVUtils.INPUT_PREFIX + firstRowInputVariableColumnNames.get(0));

    List<String> firstRowOutputVariableColumnNames = Lists.newArrayList(toMap.get(0).getOutputVariables().keySet());
    excludedColumns.add(CSVUtils.OUTPUT_PREFIX + firstRowOutputVariableColumnNames.get(2));

    //when
    List<String[]> result = CSVUtils.mapRawDecisionReportInstances(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    // 6 fields, 7 inputs, 14 outputs - 1 excluded fields
    assertThat(result.get(0).length, is(6 + 7 + 14 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

}