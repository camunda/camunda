package org.camunda.optimize.dto.optimize.query.report.single.group;

import org.camunda.optimize.dto.optimize.query.report.single.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class StartDateGroupByDto extends GroupByDto<StartDateGroupByValueDto> {

  public StartDateGroupByDto() {
    this.type = ReportConstants.GROUP_BY_START_DATE_TYPE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getUnit();
  }
}
