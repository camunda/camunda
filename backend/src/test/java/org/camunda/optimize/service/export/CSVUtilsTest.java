package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;


public class CSVUtilsTest {

  @Test
  public void testMapping() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    //when
    List<String[]> result = CSVUtils.map(toMap);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(9));
  }


  @Test
  public void testMapping_withExcludingField() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    excludedColumns.add(RawDataProcessInstanceDto.class.getDeclaredFields()[0].getName());

    //when
    List<String[]> result = CSVUtils.map(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(9 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }

  @Test
  public void testMapping_withExcludingVariable() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();
    Set<String> excludedColumns = new HashSet<>();
    Set<String> firstRowVariableColumnNames = toMap.get(0).getVariables().keySet().stream()
      .map(variableKey -> "variable:" + variableKey)
      .collect(Collectors.toSet());
    excludedColumns.add(
      firstRowVariableColumnNames.stream()
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Need at least one variable"))
    );

    //when
    List<String[]> result = CSVUtils.map(toMap, 10, null, excludedColumns);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(9 - excludedColumns.size()));
    assertThat(Arrays.asList(result.get(0)), not(hasItems(excludedColumns)));
  }
}