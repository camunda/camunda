package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;

import java.util.List;

public class ShortVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {

  protected ShortVariableFilterDataDto() {
    this(null, null);
  }

  public ShortVariableFilterDataDto(String operator, List<String> values) {
    type = VariableType.SHORT;
    OperatorMultipleValuesVariableFilterSubDataDto dataDto = new OperatorMultipleValuesVariableFilterSubDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(values);
    setData(dataDto);
  }

}
