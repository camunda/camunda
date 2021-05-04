/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.incidents;

public class IncidentErrorTypeDto {

  private String errorType;

  private int count;

  public IncidentErrorTypeDto() {
  }

  public IncidentErrorTypeDto(String errorType, int count) {
    this.errorType = errorType;
    this.count = count;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
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
    return errorType != null ? errorType.equals(that.errorType) : that.errorType == null;
  }

  @Override
  public int hashCode() {
    int result = errorType != null ? errorType.hashCode() : 0;
    result = 31 * result + count;
    return result;
  }
}
