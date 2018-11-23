package org.camunda.optimize.dto.optimize.query.report.single.group;

import org.camunda.optimize.dto.optimize.query.report.single.group.value.NoneGroupByValueDto;

public class NoneGroupByDto extends GroupByDto<NoneGroupByValueDto> {

  public NoneGroupByDto() {
    this.type = "none";
  }
}
