package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
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
