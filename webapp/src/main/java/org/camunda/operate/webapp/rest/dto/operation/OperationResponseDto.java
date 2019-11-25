/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.operation;

public class OperationResponseDto {

  private String batchOperationId;

  private int count;

  private String reason;

  public OperationResponseDto() {
  }

  public OperationResponseDto(String batchOperationId, int count) {
    this.batchOperationId = batchOperationId;
    this.count = count;
  }

  public OperationResponseDto(String batchOperationId, int count, String reason) {
    this.batchOperationId = batchOperationId;
    this.count = count;
    this.reason = reason;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public void setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
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
    if (batchOperationId != null ? !batchOperationId.equals(that.batchOperationId) : that.batchOperationId != null)
      return false;
    return reason != null ? reason.equals(that.reason) : that.reason == null;

  }

  @Override
  public int hashCode() {
    int result = batchOperationId != null ? batchOperationId.hashCode() : 0;
    result = 31 * result + count;
    result = 31 * result + (reason != null ? reason.hashCode() : 0);
    return result;
  }
}
