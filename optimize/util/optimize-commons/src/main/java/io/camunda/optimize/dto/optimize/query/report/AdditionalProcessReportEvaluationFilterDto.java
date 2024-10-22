/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.ArrayList;
import java.util.List;

public class AdditionalProcessReportEvaluationFilterDto {

  protected List<ProcessFilterDto<?>> filter = new ArrayList<>();

  public AdditionalProcessReportEvaluationFilterDto(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  public AdditionalProcessReportEvaluationFilterDto() {}

  public List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AdditionalProcessReportEvaluationFilterDto;
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
    return "AdditionalProcessReportEvaluationFilterDto(filter=" + getFilter() + ")";
  }
}
