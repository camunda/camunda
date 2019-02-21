package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

public class IntegerVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  
  protected IntegerVariableFilterDataDto() {
    this(null, null);
  }

  public IntegerVariableFilterDataDto(String operator, List<String> values) {
    type = VariableType.INTEGER;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
