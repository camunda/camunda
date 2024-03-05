/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.queries;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;

public class DateFilter {

  @Schema(
      description =
          "Start date range to search from in date-time format outlined in section 5.6 of the RFC 3339 profile of the ISO 8601 standard.")
  private Date from;

  @Schema(
      description =
          "End date range to search to in date-time format outlined in section 5.6 of the RFC 3339 profile of the ISO 8601 standard.")
  private Date to;

  public Date getTo() {
    return to;
  }

  public DateFilter setTo(Date to) {
    this.to = to;
    return this;
  }

  public Date getFrom() {
    return from;
  }

  public DateFilter setFrom(Date from) {
    this.from = from;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DateFilter that = (DateFilter) o;
    return Objects.equals(from, that.from) && Objects.equals(to, that.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DateFilter.class.getSimpleName() + "[", "]")
        .add("from=" + from)
        .add("to=" + to)
        .toString();
  }
}
