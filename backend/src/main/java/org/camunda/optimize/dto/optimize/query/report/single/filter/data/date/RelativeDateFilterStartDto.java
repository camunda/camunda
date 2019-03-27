package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

public class RelativeDateFilterStartDto {

  protected Long value;
  protected String unit;

  public RelativeDateFilterStartDto() {
  }

  public RelativeDateFilterStartDto(Long value, String unit) {
    this.unit = unit;
    this.value = value;
  }

  public Long getValue() {
    return value;
  }

  public void setValue(Long value) {
    this.value = value;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

}
