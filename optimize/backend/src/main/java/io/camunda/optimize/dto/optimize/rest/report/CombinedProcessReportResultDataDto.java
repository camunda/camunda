/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import java.util.Map;
import java.util.Objects;

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
    return Objects.hash(data, instanceCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CombinedProcessReportResultDataDto<?> that = (CombinedProcessReportResultDataDto<?>) o;
    return instanceCount == that.instanceCount && Objects.equals(data, that.data);
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
