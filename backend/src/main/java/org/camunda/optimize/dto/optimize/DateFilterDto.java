package org.camunda.optimize.dto.optimize;

import java.util.Date;

/**
 * @author Askar Akhmerov
 */
public class DateFilterDto {

  public static String START_DATE = "start_date";
  public static String END_DATE = "end_date";

  public static String GRATER_OR_EQUAL = ">=";
  public static String LESS_OR_EQUAL = "<=";
  public static String LESS = "<";
  public static String GRATER = ">";

  protected String type;
  protected String operator;
  protected Date value;


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

  public Date getValue() {
    return value;
  }

  public void setValue(Date value) {
    this.value = value;
  }

}
