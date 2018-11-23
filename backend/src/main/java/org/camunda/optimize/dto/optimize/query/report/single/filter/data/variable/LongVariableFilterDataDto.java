package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

import static org.camunda.optimize.service.util.VariableHelper.LONG_TYPE;

public class LongVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  public LongVariableFilterDataDto() {
    type = LONG_TYPE;
  }

  public LongVariableFilterDataDto(String operator, List<String> values) {
    type = LONG_TYPE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }

}