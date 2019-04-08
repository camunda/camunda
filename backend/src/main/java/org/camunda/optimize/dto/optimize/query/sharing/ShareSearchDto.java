/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.ArrayList;
import java.util.List;


public class ShareSearchDto {
  private List<String> reports = new ArrayList<>();
  private List<String> dashboards = new ArrayList<>();

  public List<String> getReports() {
    return reports;
  }

  public void setReports(List<String> reports) {
    this.reports = reports;
  }

  public List<String> getDashboards() {
    return dashboards;
  }

  public void setDashboards(List<String> dashboards) {
    this.dashboards = dashboards;
  }
}
