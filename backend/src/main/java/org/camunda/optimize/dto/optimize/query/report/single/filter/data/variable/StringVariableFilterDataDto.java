package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

public class StringVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  protected StringVariableFilterDataDto() {
    this(null, null);
  }

  public StringVariableFilterDataDto(String operator, List<String> values) {
    type = VariableType.STRING;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
