package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.variable.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class VariableFilterUtilHelper {

  public static VariableFilterDto createBooleanVariableFilter(String variableName, String value) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(value);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createStringVariableFilter(String variableName, String operator, String variableValue) {
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    StringVariableFilterDataDto data = new StringVariableFilterDataDto(operator, values);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createLongVariableFilter(String variableName, String operator, String variableValue) {
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    LongVariableFilterDataDto data = new LongVariableFilterDataDto(operator, values);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createShortVariableFilter(String variableName, String operator, String variableValue) {
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    ShortVariableFilterDataDto data = new ShortVariableFilterDataDto(operator, values);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createIntegerVariableFilter(String variableName, String operator, String variableValue) {
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    IntegerVariableFilterDataDto data = new IntegerVariableFilterDataDto(operator, values);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createDoubleVariableFilter(String variableName, String operator, String variableValue) {
    List<String> values = new ArrayList<>();
    values.add(variableValue);
    DoubleVariableFilterDataDto data = new DoubleVariableFilterDataDto(operator, values);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static VariableFilterDto createDateVariableFilter(String variableName, OffsetDateTime start, OffsetDateTime end) {
    DateVariableFilterDataDto data = new DateVariableFilterDataDto(start, end);
    data.setName(variableName);

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }
}
