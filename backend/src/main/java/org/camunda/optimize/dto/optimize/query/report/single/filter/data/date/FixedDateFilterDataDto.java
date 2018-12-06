package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.startDate.DateFilterType;

import java.time.OffsetDateTime;

public class FixedDateFilterDataDto extends DateFilterDataDto<OffsetDateTime> {

  public FixedDateFilterDataDto() {
    this.type = DateFilterType.FIXED;
  }

}
