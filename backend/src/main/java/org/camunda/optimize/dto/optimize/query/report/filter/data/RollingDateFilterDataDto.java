package org.camunda.optimize.dto.optimize.query.report.filter.data;

/**
 * @author Askar Akhmerov
 */
public class RollingDateFilterDataDto implements FilterDataDto  {

  protected Long value;
  protected String unit;

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
