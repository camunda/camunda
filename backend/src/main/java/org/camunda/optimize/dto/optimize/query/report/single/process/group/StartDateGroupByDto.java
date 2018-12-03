package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;

public class StartDateGroupByDto extends ProcessGroupByDto<StartDateGroupByValueDto> {

  public StartDateGroupByDto() {
    this.type = ProcessGroupByType.START_DATE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getUnit();
  }


}
