package org.camunda.optimize.dto.optimize.query.report.group;

import org.camunda.optimize.dto.optimize.query.report.group.value.NoneGroupByValueDto;

public class NoneGroupByDto extends GroupByDto<NoneGroupByValueDto> {

  public NoneGroupByDto() {
    this.type = "none";
  }
}
