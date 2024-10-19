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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
