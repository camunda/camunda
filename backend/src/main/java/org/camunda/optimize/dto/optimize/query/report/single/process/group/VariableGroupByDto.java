package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;

public class VariableGroupByDto extends ProcessGroupByDto<VariableGroupByValueDto> {

  public VariableGroupByDto() {
    this.type = ProcessGroupByType.VARIABLE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getName() + "_" + getValue().getType();
  }
}
