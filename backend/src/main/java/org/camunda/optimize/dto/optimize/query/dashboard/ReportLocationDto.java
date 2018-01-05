package org.camunda.optimize.dto.optimize.query.dashboard;

public class ReportLocationDto {

  protected String id;
  protected PositionDto position;
  protected DimensionDto dimensions;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public PositionDto getPosition() {
    return position;
  }

  public void setPosition(PositionDto position) {
    this.position = position;
  }

  public DimensionDto getDimensions() {
    return dimensions;
  }

  public void setDimensions(DimensionDto dimensions) {
    this.dimensions = dimensions;
  }
}
