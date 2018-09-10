package org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate;

import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {


  public RelativeDateFilterDataDto() {
    this.type = ReportConstants.RELATIVE_DATE_FILTER;
  }

}
