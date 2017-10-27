package org.camunda.optimize.dto.optimize.query.dashboard;

public class ReportLocationDto {

  protected String reportId;
  protected PositionDto position;
  protected DimensionDto dimension;

  public String getReportId() {
    return reportId;
  }

  public void setReportId(String reportId) {
    this.reportId = reportId;
  }

  public PositionDto getPosition() {
    return position;
  }

  public void setPosition(PositionDto position) {
    this.position = position;
  }

  public DimensionDto getDimension() {
    return dimension;
  }

  public void setDimension(DimensionDto dimension) {
    this.dimension = dimension;
  }
}
