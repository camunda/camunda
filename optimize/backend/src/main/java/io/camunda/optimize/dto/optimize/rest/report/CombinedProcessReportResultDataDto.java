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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
