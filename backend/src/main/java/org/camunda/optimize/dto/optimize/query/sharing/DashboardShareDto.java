package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DashboardShareDto {

  private String id;
  private SharedResourceType type;
  private String dashboardId;
  private List<String> reportShares;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public SharedResourceType getType() {
    return type;
  }

  public void setType(SharedResourceType type) {
    this.type = type;
  }

  public String getDashboardId() {
    return dashboardId;
  }

  public void setDashboardId(String dashboardId) {
    this.dashboardId = dashboardId;
  }

  public List<String> getReportShares() {
    return reportShares;
  }

  public void setReportShares(List<String> reportShares) {
    this.reportShares = reportShares;
  }
}
