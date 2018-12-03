package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.variable.data.BooleanVariableFilterSubDataDto;

import static org.camunda.optimize.service.util.VariableHelper.BOOLEAN_TYPE;

public class BooleanVariableFilterDataDto extends VariableFilterDataDto<BooleanVariableFilterSubDataDto> {
  public BooleanVariableFilterDataDto() {
    this.type = BOOLEAN_TYPE;
  }

  public BooleanVariableFilterDataDto(String value) {
    this.type = BOOLEAN_TYPE;
    BooleanVariableFilterSubDataDto dataDto = new BooleanVariableFilterSubDataDto();
    dataDto.setValue(value);
    setData(dataDto);
  }
}
