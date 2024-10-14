/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import java.time.OffsetDateTime;

public class FixedDateFilterDataDto extends DateFilterDataDto<OffsetDateTime> {

  public FixedDateFilterDataDto() {
    this(null, null);
  }

  public FixedDateFilterDataDto(final OffsetDateTime dateTime, final OffsetDateTime end) {
    super(DateFilterType.FIXED, dateTime, end);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FixedDateFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FixedDateFilterDataDto)) {
      return false;
    }
    final FixedDateFilterDataDto other = (FixedDateFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }
}
