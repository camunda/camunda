package org.camunda.optimize.dto.optimize.query.sharing;

/**
 * @author Askar Akhmerov
 */
public class EvaluatedDashboardShareDto extends DashboardShareDto {

  private DashboardDefinitionShareDto dashboard;

  public EvaluatedDashboardShareDto () {
    this(null);
  }

  public EvaluatedDashboardShareDto(DashboardShareDto base) {
    if (base != null) {
      this.setId(base.getId());
      this.setType(base.getType());
      this.setDashboardId(base.getDashboardId());
    }
  }

  public DashboardDefinitionShareDto getDashboard() {
    return dashboard;
  }

  public void setDashboard(DashboardDefinitionShareDto dashboard) {
    this.dashboard = dashboard;
  }
}
