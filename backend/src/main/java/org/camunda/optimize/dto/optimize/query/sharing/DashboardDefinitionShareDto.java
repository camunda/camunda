package org.camunda.optimize.dto.optimize.query.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class DashboardDefinitionShareDto extends BaseDashboardDefinitionDto {

  private List<ReportShareLocationDto> reportShares = new ArrayList<>();

  public List<ReportShareLocationDto> getReportShares() {
    return reportShares;
  }

  public void setReportShares(List<ReportShareLocationDto> reportShares) {
    this.reportShares = reportShares;
  }

  public static DashboardDefinitionShareDto of(BaseDashboardDefinitionDto dashboardDefinition) {
    DashboardDefinitionShareDto result = new DashboardDefinitionShareDto();
    result.setId(dashboardDefinition.getId());
    result.setCreated(dashboardDefinition.getCreated());
    result.setLastModified(dashboardDefinition.getLastModified());
    result.setLastModifier(dashboardDefinition.getLastModifier());
    result.setName(dashboardDefinition.getName());
    result.setOwner(dashboardDefinition.getOwner());
    return result;
  }
}
