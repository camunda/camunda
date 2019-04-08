/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;

import java.util.List;

public class DashboardShareDto {

  private String id;
  private String dashboardId;
  private List<ReportLocationDto> reportShares;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDashboardId() {
    return dashboardId;
  }

  public void setDashboardId(String dashboardId) {
    this.dashboardId = dashboardId;
  }

  public List<ReportLocationDto> getReportShares() {
    return reportShares;
  }

  public void setReportShares(List<ReportLocationDto> reportShares) {
    this.reportShares = reportShares;
  }
}
