package org.camunda.optimize.dto.optimize;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class DateDto {

  public static String START_DATE = "start_date";
  public static String END_DATE = "end_date";

  public static String GRATER_OR_EQUAL = ">=";
  public static String LESS_OR_EQUAL = "<=";
  public static String LESS = "<";
  public static String GRATER = ">";

  public static Set<String> GRATER_OPERATORS = new HashSet<>(Arrays.asList(GRATER,GRATER_OR_EQUAL));
  public static Set<String> LESS_OPERATORS = new HashSet<>(Arrays.asList(LESS,LESS_OR_EQUAL));

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

  public boolean isLowerBoundary() {
    return GRATER_OPERATORS.contains(this.getOperator());
  }

  public boolean isUpperBoundary() {
    return LESS_OPERATORS.contains(this.getOperator());
  }
}
