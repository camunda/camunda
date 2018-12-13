package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.BooleanVariableFilterSubDataDto;

public class BooleanVariableFilterDataDto extends VariableFilterDataDto<BooleanVariableFilterSubDataDto> {
  protected BooleanVariableFilterDataDto() {
    this(null);
  }

  public BooleanVariableFilterDataDto(String value) {
    this.type = VariableType.BOOLEAN;
    BooleanVariableFilterSubDataDto dataDto = new BooleanVariableFilterSubDataDto();
    dataDto.setValue(value);
    setData(dataDto);
  }
}
