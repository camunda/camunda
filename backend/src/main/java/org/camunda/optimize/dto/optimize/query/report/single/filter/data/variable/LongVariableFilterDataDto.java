package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

public class LongVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  protected LongVariableFilterDataDto() {
    this(null, null);
  }

  public LongVariableFilterDataDto(String operator, List<String> values) {
    type = VariableType.LONG;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }

}