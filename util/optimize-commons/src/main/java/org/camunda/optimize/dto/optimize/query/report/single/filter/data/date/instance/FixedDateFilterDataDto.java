/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;

import java.time.OffsetDateTime;

@EqualsAndHashCode
public class FixedDateFilterDataDto extends DateFilterDataDto<OffsetDateTime> {
  public FixedDateFilterDataDto() {
    this(null, null);
  }

  public FixedDateFilterDataDto(final OffsetDateTime dateTime, final OffsetDateTime end) {
    super(DateFilterType.FIXED, dateTime, end);
  }
}
