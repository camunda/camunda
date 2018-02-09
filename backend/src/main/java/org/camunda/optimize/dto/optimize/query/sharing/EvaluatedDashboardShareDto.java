package org.camunda.optimize.dto.optimize.query.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.BaseDashboardDefinitionDto;

/**
 * @author Askar Akhmerov
 */
public class EvaluatedDashboardShareDto extends SharingDto {

  private DashboardDefinitionShareDto dashboard;

  public EvaluatedDashboardShareDto () {
    this(null);
  }

  public EvaluatedDashboardShareDto(SharingDto base) {
    if (base != null) {
      this.setId(base.getId());
      this.setType(base.getType());
      this.setResourceId(base.getResourceId());
    }
  }

  public DashboardDefinitionShareDto getDashboard() {
    return dashboard;
  }

  public void setDashboard(DashboardDefinitionShareDto dashboard) {
    this.dashboard = dashboard;
  }
}
