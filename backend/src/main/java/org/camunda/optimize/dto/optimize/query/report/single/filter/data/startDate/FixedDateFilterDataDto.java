package org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate;

import org.camunda.optimize.service.es.report.command.util.ReportConstants;

import java.time.OffsetDateTime;

public class FixedDateFilterDataDto extends DateFilterDataDto<OffsetDateTime> {

  public FixedDateFilterDataDto() {
    this.type = ReportConstants.FIXED_DATE_FILTER;
  }

}
