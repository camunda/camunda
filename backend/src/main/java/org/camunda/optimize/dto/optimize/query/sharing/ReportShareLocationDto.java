package org.camunda.optimize.dto.optimize.query.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;

public class ReportShareLocationDto extends ReportLocationDto {
  private String shareId;

  public String getShareId() {
    return shareId;
  }

  public void setShareId(String shareId) {
    this.shareId = shareId;
  }
}
