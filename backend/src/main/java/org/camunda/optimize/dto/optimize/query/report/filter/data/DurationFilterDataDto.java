package org.camunda.optimize.dto.optimize.query.report.filter.data;

/**
 * @author Askar Akhmerov
 */
public class DurationFilterDataDto extends RollingDateFilterDataDto {
  public static final String DURATION = "durationInMs";

  protected String operator;

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }
}
