package org.camunda.optimize.dto.optimize.query.report.filter.data.startDate;

import org.camunda.optimize.service.es.report.command.util.ReportConstants;

import java.time.OffsetDateTime;

public class FixedStartDateFilterDataDto extends StartDateFilterDataDto<OffsetDateTime> {

  public FixedStartDateFilterDataDto() {
    this.type = ReportConstants.FIXED_DATE_FILTER;
  }

}
