package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {

  public RelativeDateFilterDataDto() {
    this.type = DateFilterType.RELATIVE;
  }

}
