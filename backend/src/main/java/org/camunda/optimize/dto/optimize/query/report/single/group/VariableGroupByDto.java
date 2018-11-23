package org.camunda.optimize.dto.optimize.query.report.single.group;

import org.camunda.optimize.dto.optimize.query.report.single.group.value.VariableGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class VariableGroupByDto extends GroupByDto<VariableGroupByValueDto> {

  public VariableGroupByDto() {
    this.type = ReportConstants.GROUP_BY_VARIABLE_TYPE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getName() + "_" + getValue().getType();
  }
}
