package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

import static org.camunda.optimize.service.util.ProcessVariableHelper.STRING_TYPE;

public class StringVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  public StringVariableFilterDataDto() {
    type = STRING_TYPE;
  }

  public StringVariableFilterDataDto(String operator, List<String> values) {
    type = STRING_TYPE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
