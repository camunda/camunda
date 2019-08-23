/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.operation;

public class OperationResponseDto {

  private int count;

  private String reason;

  public OperationResponseDto() {
  }

  public OperationResponseDto(int count) {
    this.count = count;
  }

  public OperationResponseDto(int count, String reason) {
    this.count = count;
    this.reason = reason;
  }

  /**
   * Number of scheduled operations.
   * @return
   */
  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OperationResponseDto that = (OperationResponseDto) o;

    if (count != that.count)
      return false;
    return reason != null ? reason.equals(that.reason) : that.reason == null;
  }

  @Override
  public int hashCode() {
    int result = count;
    result = 31 * result + (reason != null ? reason.hashCode() : 0);
    return result;
  }
}
