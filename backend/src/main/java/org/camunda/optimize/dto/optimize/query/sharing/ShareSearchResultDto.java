package org.camunda.optimize.dto.optimize.query.sharing;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class ShareSearchResultDto {
  private List<ShareStatusDto> reports = new ArrayList<>();
  private List<ShareStatusDto> dashboards = new ArrayList<>();

  public List<ShareStatusDto> getReports() {
    return reports;
  }

  public void setReports(List<ShareStatusDto> reports) {
    this.reports = reports;
  }

  public List<ShareStatusDto> getDashboards() {
    return dashboards;
  }

  public void setDashboards(List<ShareStatusDto> dashboards) {
    this.dashboards = dashboards;
  }
}
