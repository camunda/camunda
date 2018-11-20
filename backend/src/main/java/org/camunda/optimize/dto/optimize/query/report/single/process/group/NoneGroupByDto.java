package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.NoneGroupByValueDto;

public class NoneGroupByDto extends ProcessGroupByDto<NoneGroupByValueDto> {

  public NoneGroupByDto() {
    this.type = ProcessGroupByType.NONE;
  }
}
