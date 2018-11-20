package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

import static org.camunda.optimize.service.util.VariableHelper.DOUBLE_TYPE;

public class DoubleVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  public DoubleVariableFilterDataDto() {
    type = DOUBLE_TYPE;
  }

  public DoubleVariableFilterDataDto(String operator, List<String> values) {
    type = DOUBLE_TYPE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }
}
