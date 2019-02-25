package org.camunda.optimize.dto.optimize.query.report.combined;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_CONFIGURATION_COLOR;

public class CombinedReportItemDto {

  private String id;
  private String color = DEFAULT_CONFIGURATION_COLOR;

  protected CombinedReportItemDto() {
  }

  public CombinedReportItemDto(String id, String color) {
    this(id);
    this.color = color;
  }

  public CombinedReportItemDto(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }
}
