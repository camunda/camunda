/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import java.time.ZoneId;

public final class FilterContext {

  private final ZoneId timezone;
  private final boolean userTaskReport;

  FilterContext(final ZoneId timezone, final boolean userTaskReport) {
    this.timezone = timezone;
    this.userTaskReport = userTaskReport;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public boolean isUserTaskReport() {
    return userTaskReport;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $timezone = getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    result = result * PRIME + (isUserTaskReport() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FilterContext)) {
      return false;
    }
    final FilterContext other = (FilterContext) o;
    final Object this$timezone = getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    if (isUserTaskReport() != other.isUserTaskReport()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "FilterContext(timezone="
        + getTimezone()
        + ", userTaskReport="
        + isUserTaskReport()
        + ")";
  }

  public static FilterContextBuilder builder() {
    return new FilterContextBuilder();
  }

  public static class FilterContextBuilder {

    private ZoneId timezone;
    private boolean userTaskReport;

    FilterContextBuilder() {}

    public FilterContextBuilder timezone(final ZoneId timezone) {
      this.timezone = timezone;
      return this;
    }

    public FilterContextBuilder userTaskReport(final boolean userTaskReport) {
      this.userTaskReport = userTaskReport;
      return this;
    }

    public FilterContext build() {
      return new FilterContext(timezone, userTaskReport);
    }

    @Override
    public String toString() {
      return "FilterContext.FilterContextBuilder(timezone="
          + timezone
          + ", userTaskReport="
          + userTaskReport
          + ")";
    }
  }
}
