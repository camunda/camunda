/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sharing;

import java.util.ArrayList;
import java.util.List;

public class ShareSearchRequestDto {

  private List<String> reports = new ArrayList<>();
  private List<String> dashboards = new ArrayList<>();

  public ShareSearchRequestDto() {}

  public List<String> getReports() {
    return reports;
  }

  public void setReports(final List<String> reports) {
    this.reports = reports;
  }

  public List<String> getDashboards() {
    return dashboards;
  }

  public void setDashboards(final List<String> dashboards) {
    this.dashboards = dashboards;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ShareSearchRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reports = getReports();
    result = result * PRIME + ($reports == null ? 43 : $reports.hashCode());
    final Object $dashboards = getDashboards();
    result = result * PRIME + ($dashboards == null ? 43 : $dashboards.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ShareSearchRequestDto)) {
      return false;
    }
    final ShareSearchRequestDto other = (ShareSearchRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reports = getReports();
    final Object other$reports = other.getReports();
    if (this$reports == null ? other$reports != null : !this$reports.equals(other$reports)) {
      return false;
    }
    final Object this$dashboards = getDashboards();
    final Object other$dashboards = other.getDashboards();
    if (this$dashboards == null
        ? other$dashboards != null
        : !this$dashboards.equals(other$dashboards)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ShareSearchRequestDto(reports="
        + getReports()
        + ", dashboards="
        + getDashboards()
        + ")";
  }
}
