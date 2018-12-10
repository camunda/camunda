package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

import static org.camunda.optimize.service.util.ProcessVariableHelper.INTEGER_TYPE;

public class IntegerVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  public IntegerVariableFilterDataDto() {
    type = INTEGER_TYPE;
  }

  public IntegerVariableFilterDataDto(String operator, List<String> values) {
    type = INTEGER_TYPE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
