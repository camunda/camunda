package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
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
