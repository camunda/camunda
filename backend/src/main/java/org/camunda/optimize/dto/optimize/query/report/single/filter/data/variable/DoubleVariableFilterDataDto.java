package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

public class DoubleVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  protected DoubleVariableFilterDataDto() {
    this(null, null);
  }

  public DoubleVariableFilterDataDto(String operator, List<String> values) {
    type = VariableType.DOUBLE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
