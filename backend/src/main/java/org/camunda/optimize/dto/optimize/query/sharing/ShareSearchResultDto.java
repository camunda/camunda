/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.HashMap;
import java.util.Map;


public class ShareSearchResultDto {
  private Map<String, Boolean> reports = new HashMap<>();
  private Map<String, Boolean> dashboards = new HashMap<>();

  public Map<String, Boolean> getReports() {
    return reports;
  }

  public void setReports(Map<String, Boolean> reports) {
    this.reports = reports;
  }

  public Map<String, Boolean> getDashboards() {
    return dashboards;
  }

  public void setDashboards(Map<String, Boolean> dashboards) {
    this.dashboards = dashboards;
  }
}
