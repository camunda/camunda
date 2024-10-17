/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import java.util.ArrayList;
import java.util.List;

public class ReportResultResponseDto<T> {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<MeasureResponseDto<T>> measures = new ArrayList<>();
  private PaginationDto pagination;

  public ReportResultResponseDto(
      final long instanceCount,
      final long instanceCountWithoutFilters,
      final List<MeasureResponseDto<T>> measures,
      final PaginationDto pagination) {
    this.instanceCount = instanceCount;
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
    this.measures = measures;
    this.pagination = pagination;
  }

  public ReportResultResponseDto() {}

  public void addMeasure(final MeasureResponseDto<T> measure) {
    measures.add(measure);
  }

  @JsonIgnore
  public T getFirstMeasureData() {
    return getMeasures().get(0).getData();
  }

  @JsonIgnore
  public T getData() {
    return getFirstMeasureData();
  }

  // here for API compatibility as the frontend currently makes use of this property
  public ResultType getType() {
    return getMeasures().stream().findFirst().map(MeasureResponseDto::getType).orElse(null);
  }

  public long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final long instanceCount) {
    this.instanceCount = instanceCount;
  }

  public long getInstanceCountWithoutFilters() {
    return instanceCountWithoutFilters;
  }

  public void setInstanceCountWithoutFilters(final long instanceCountWithoutFilters) {
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
  }

  public List<MeasureResponseDto<T>> getMeasures() {
    return measures;
  }

  public void setMeasures(final List<MeasureResponseDto<T>> measures) {
    this.measures = measures;
  }

  public PaginationDto getPagination() {
    return pagination;
  }

  public void setPagination(final PaginationDto pagination) {
    this.pagination = pagination;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportResultResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $instanceCount = getInstanceCount();
    result = result * PRIME + (int) ($instanceCount >>> 32 ^ $instanceCount);
    final long $instanceCountWithoutFilters = getInstanceCountWithoutFilters();
    result =
        result * PRIME + (int) ($instanceCountWithoutFilters >>> 32 ^ $instanceCountWithoutFilters);
    final Object $measures = getMeasures();
    result = result * PRIME + ($measures == null ? 43 : $measures.hashCode());
    final Object $pagination = getPagination();
    result = result * PRIME + ($pagination == null ? 43 : $pagination.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReportResultResponseDto)) {
      return false;
    }
    final ReportResultResponseDto<?> other = (ReportResultResponseDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getInstanceCount() != other.getInstanceCount()) {
      return false;
    }
    if (getInstanceCountWithoutFilters() != other.getInstanceCountWithoutFilters()) {
      return false;
    }
    final Object this$measures = getMeasures();
    final Object other$measures = other.getMeasures();
    if (this$measures == null ? other$measures != null : !this$measures.equals(other$measures)) {
      return false;
    }
    final Object this$pagination = getPagination();
    final Object other$pagination = other.getPagination();
    if (this$pagination == null
        ? other$pagination != null
        : !this$pagination.equals(other$pagination)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ReportResultResponseDto(instanceCount="
        + getInstanceCount()
        + ", instanceCountWithoutFilters="
        + getInstanceCountWithoutFilters()
        + ", measures="
        + getMeasures()
        + ", pagination="
        + getPagination()
        + ")";
  }
}
