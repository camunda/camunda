/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sharing;

import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import java.util.List;
import java.util.Objects;

public class DashboardShareRestDto {

  private String id;
  private String dashboardId;
  private List<DashboardReportTileDto> tileShares;

  public DashboardShareRestDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getDashboardId() {
    return dashboardId;
  }

  public void setDashboardId(final String dashboardId) {
    this.dashboardId = dashboardId;
  }

  public List<DashboardReportTileDto> getTileShares() {
    return tileShares;
  }

  public void setTileShares(final List<DashboardReportTileDto> tileShares) {
    this.tileShares = tileShares;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardShareRestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, dashboardId, tileShares);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DashboardShareRestDto that = (DashboardShareRestDto) o;
    return Objects.equals(id, that.id)
        && Objects.equals(dashboardId, that.dashboardId)
        && Objects.equals(tileShares, that.tileShares);
  }

  @Override
  public String toString() {
    return "DashboardShareRestDto(id="
        + getId()
        + ", dashboardId="
        + getDashboardId()
        + ", tileShares="
        + getTileShares()
        + ")";
  }
}
