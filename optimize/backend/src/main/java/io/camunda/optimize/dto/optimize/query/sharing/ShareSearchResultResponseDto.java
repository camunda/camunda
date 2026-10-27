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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ShareSearchResultResponseDto that = (ShareSearchResultResponseDto) o;
    return Objects.equals(reports, that.reports) && Objects.equals(dashboards, that.dashboards);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reports, dashboards);
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
