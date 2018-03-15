package org.camunda.optimize.dto.optimize.query.sharing;

import java.io.Serializable;

/**
 * @author Askar Akhmerov
 */
public class ReportShareDto implements Serializable {
  private String id;
  private String reportId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(String reportId) {
    this.reportId = reportId;
  }
}
