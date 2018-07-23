package org.camunda.optimize.dto.optimize.query.report.filter.data.startDate;

import org.camunda.optimize.service.es.report.command.util.ReportConstants;

public class RelativeStartDateFilterDataDto extends StartDateFilterDataDto<RelativeStartDateFilterStartDto> {


  public RelativeStartDateFilterDataDto() {
    this.type = ReportConstants.RELATIVE_DATE_FILTER;
  }

}
