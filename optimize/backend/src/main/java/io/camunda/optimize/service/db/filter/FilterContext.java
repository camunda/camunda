/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.filter;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
