package org.camunda.optimize.dto.optimize.query.alert;

/**
 * @author Askar Akhmerov
 */
public class AlertInterval {
  protected int value;
  protected String unit;

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }
}
