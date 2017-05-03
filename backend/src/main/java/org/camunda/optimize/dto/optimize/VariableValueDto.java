package org.camunda.optimize.dto.optimize;

import java.text.SimpleDateFormat;
import java.util.Date;

public class VariableValueDto implements OptimizeDto {

  protected String stringVal;
  protected Integer integerVal;
  protected Long longVal;
  protected Boolean booleanVal;
  protected Date dateVal;
  protected Short shortVal;
  protected Double doubleVal;

  public VariableValueDto(String stringVal) {
    this.stringVal = stringVal;
  }

  public VariableValueDto(Integer integerVal) {
    this.integerVal = integerVal;
  }

  public VariableValueDto(Long longVal) {
    this.longVal = longVal;
  }

  public VariableValueDto(Boolean booleanVal) {
    this.booleanVal = booleanVal;
  }

  public VariableValueDto(Date dateVal) {
    this.dateVal = dateVal;
  }

  public VariableValueDto(Short shortVal) {
    this.shortVal = shortVal;
  }

  public VariableValueDto(Double doubleVal) {
    this.doubleVal = doubleVal;
  }

  public String getStringVal() {
    return stringVal;
  }

  public void setStringVal(String stringVal) {
    this.stringVal = stringVal;
  }

  public Integer getIntegerVal() {
    return integerVal;
  }

  public void setIntegerVal(Integer integerVal) {
    this.integerVal = integerVal;
  }

  public Long getLongVal() {
    return longVal;
  }

  public void setLongVal(Long longVal) {
    this.longVal = longVal;
  }

  public Boolean getBooleanVal() {
    return booleanVal;
  }

  public void setBooleanVal(Boolean booleanVal) {
    this.booleanVal = booleanVal;
  }

  public Date getDateVal() {
    return dateVal;
  }

  public void setDateVal(Date dateVal) {
    this.dateVal = dateVal;
  }

  public Short getShortVal() {
    return shortVal;
  }

  public void setShortVal(Short shortVal) {
    this.shortVal = shortVal;
  }

  public Double getDoubleVal() {
    return doubleVal;
  }

  public void setDoubleVal(Double doubleVal) {
    this.doubleVal = doubleVal;
  }

  @Override
  public String toString() {
    return toString("");
  }

  public String toString(String dateFormat) {
    if (stringVal != null)
      return stringVal;

    if (integerVal != null)
      return integerVal.toString();

    if (longVal != null)
      return longVal.toString();

    if (booleanVal != null)
      return booleanVal.toString();

    if (dateVal != null) {
      SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
      return sdf.format(dateVal);
    }

    if (shortVal != null)
      return shortVal.toString();

    if (doubleVal != null)
      return doubleVal.toString();

    return null;
  }
}
