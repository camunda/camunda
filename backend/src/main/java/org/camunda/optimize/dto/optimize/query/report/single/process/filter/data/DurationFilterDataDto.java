package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;


import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

public class DurationFilterDataDto implements FilterDataDto {
  public static final String DURATION = "durationInMs";

  protected Long value;
  protected String unit;
  protected String operator;

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

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }
}
