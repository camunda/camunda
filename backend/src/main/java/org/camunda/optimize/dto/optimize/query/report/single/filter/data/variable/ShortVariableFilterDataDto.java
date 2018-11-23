package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

import static org.camunda.optimize.service.util.VariableHelper.SHORT_TYPE;

public class ShortVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  public ShortVariableFilterDataDto() {
    type = SHORT_TYPE;
  }

  public ShortVariableFilterDataDto(String operator, List<String> values) {
    type = SHORT_TYPE;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }

}
