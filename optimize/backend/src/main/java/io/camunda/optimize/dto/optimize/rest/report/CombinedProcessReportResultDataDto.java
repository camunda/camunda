/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import java.util.Map;

public class CombinedProcessReportResultDataDto<T> {

  protected Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> data;
  private long instanceCount;

  public CombinedProcessReportResultDataDto(
      final Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> data,
      final long instanceCount) {
    this.data = data;
    this.instanceCount = instanceCount;
  }

  protected CombinedProcessReportResultDataDto() {}

  public Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> getData() {
    return data;
  }

  public void setData(final Map<String, AuthorizedProcessReportEvaluationResponseDto<T>> data) {
    this.data = data;
  }

  public long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final long instanceCount) {
    this.instanceCount = instanceCount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CombinedProcessReportResultDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $data = getData();
    result = result * PRIME + ($data == null ? 43 : $data.hashCode());
    final long $instanceCount = getInstanceCount();
    result = result * PRIME + (int) ($instanceCount >>> 32 ^ $instanceCount);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CombinedProcessReportResultDataDto)) {
      return false;
    }
    final CombinedProcessReportResultDataDto<?> other = (CombinedProcessReportResultDataDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$data = getData();
    final Object other$data = other.getData();
    if (this$data == null ? other$data != null : !this$data.equals(other$data)) {
      return false;
    }
    if (getInstanceCount() != other.getInstanceCount()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CombinedProcessReportResultDataDto(data="
        + getData()
        + ", instanceCount="
        + getInstanceCount()
        + ")";
  }
}
