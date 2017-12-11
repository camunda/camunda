package org.camunda.optimize.dto.optimize.query.report.filter.data;

import java.time.OffsetDateTime;

/**
 * @author Askar Akhmerov
 */
public class DateFilterDataDto implements FilterDataDto {

  public static String START_DATE = "start_date";
  public static String END_DATE = "end_date";

  protected String type;
  protected String operator;
  protected OffsetDateTime value;


  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public OffsetDateTime getValue() {
    return value;
  }

  public void setValue(OffsetDateTime value) {
    this.value = value;
  }

}
