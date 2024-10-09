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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $dashboardId = getDashboardId();
    result = result * PRIME + ($dashboardId == null ? 43 : $dashboardId.hashCode());
    final Object $tileShares = getTileShares();
    result = result * PRIME + ($tileShares == null ? 43 : $tileShares.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardShareRestDto)) {
      return false;
    }
    final DashboardShareRestDto other = (DashboardShareRestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$dashboardId = getDashboardId();
    final Object other$dashboardId = other.getDashboardId();
    if (this$dashboardId == null
        ? other$dashboardId != null
        : !this$dashboardId.equals(other$dashboardId)) {
      return false;
    }
    final Object this$tileShares = getTileShares();
    final Object other$tileShares = other.getTileShares();
    if (this$tileShares == null
        ? other$tileShares != null
        : !this$tileShares.equals(other$tileShares)) {
      return false;
    }
    return true;
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
