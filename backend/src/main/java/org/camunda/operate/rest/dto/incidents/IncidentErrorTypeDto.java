/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

public class IncidentErrorTypeDto {

  private String errorType;

  private String errorTypeTitle;

  private int count;

  public IncidentErrorTypeDto() {
  }

  public IncidentErrorTypeDto(String errorType, String errorTypeTitle, int count) {
    this.errorType = errorType;
    this.errorTypeTitle = errorTypeTitle;
    this.count = count;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorTypeTitle() {
    return errorTypeTitle;
  }

  public void setErrorTypeTitle(String errorTypeTitle) {
    this.errorTypeTitle = errorTypeTitle;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    IncidentErrorTypeDto that = (IncidentErrorTypeDto) o;

    if (count != that.count)
      return false;
    if (errorType != null ? !errorType.equals(that.errorType) : that.errorType != null)
      return false;
    return errorTypeTitle != null ? errorTypeTitle.equals(that.errorTypeTitle) : that.errorTypeTitle == null;
  }

  @Override
  public int hashCode() {
    int result = errorType != null ? errorType.hashCode() : 0;
    result = 31 * result + (errorTypeTitle != null ? errorTypeTitle.hashCode() : 0);
    result = 31 * result + count;
    return result;
  }
}
