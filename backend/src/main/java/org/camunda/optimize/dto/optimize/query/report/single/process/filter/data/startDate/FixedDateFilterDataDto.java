package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.startDate;

import java.time.OffsetDateTime;

public class FixedDateFilterDataDto extends DateFilterDataDto<OffsetDateTime> {

  public FixedDateFilterDataDto() {
    this.type = DateFilterType.FIXED;
  }

}
