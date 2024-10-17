/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sharing;

import java.util.HashMap;
import java.util.Map;

public class ShareSearchResultResponseDto {

  private Map<String, Boolean> reports = new HashMap<>();
  private Map<String, Boolean> dashboards = new HashMap<>();

  public ShareSearchResultResponseDto() {}

  public Map<String, Boolean> getReports() {
    return reports;
  }

  public void setReports(final Map<String, Boolean> reports) {
    this.reports = reports;
  }

  public Map<String, Boolean> getDashboards() {
    return dashboards;
  }

  public void setDashboards(final Map<String, Boolean> dashboards) {
    this.dashboards = dashboards;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ShareSearchResultResponseDto;
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
    return "ShareSearchResultResponseDto(reports="
        + getReports()
        + ", dashboards="
        + getDashboards()
        + ")";
  }
}
