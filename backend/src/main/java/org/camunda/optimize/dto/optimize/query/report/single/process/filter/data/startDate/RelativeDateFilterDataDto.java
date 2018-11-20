package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.startDate;

public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {

  public RelativeDateFilterDataDto() {
    this.type = DateFilterType.RELATIVE;
  }

}
