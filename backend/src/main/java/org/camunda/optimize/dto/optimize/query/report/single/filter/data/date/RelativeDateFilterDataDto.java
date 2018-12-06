package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.startDate.DateFilterType;

public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {

  public RelativeDateFilterDataDto() {
    this.type = DateFilterType.RELATIVE;
  }

}
