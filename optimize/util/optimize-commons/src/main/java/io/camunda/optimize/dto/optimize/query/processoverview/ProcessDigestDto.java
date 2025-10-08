/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import java.util.Map;
import java.util.Objects;

public class ProcessDigestDto extends ProcessDigestResponseDto {

  // This is the baseline results, or in other words the results that were included in the
  // previously sent digest
  private Map<String, String> kpiReportResults;

  public ProcessDigestDto(final Boolean enabled, final Map<String, String> kpiReportResults) {
    super(enabled);
    this.kpiReportResults = kpiReportResults;
  }

  public ProcessDigestDto(final Map<String, String> kpiReportResults) {
    this.kpiReportResults = kpiReportResults;
  }

  public ProcessDigestDto() {}

  public Map<String, String> getKpiReportResults() {
    return kpiReportResults;
  }

  public void setKpiReportResults(final Map<String, String> kpiReportResults) {
    this.kpiReportResults = kpiReportResults;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDigestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ProcessDigestDto that = (ProcessDigestDto) o;
    return Objects.equals(kpiReportResults, that.kpiReportResults);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), kpiReportResults);
  }

  @Override
  public String toString() {
    return "ProcessDigestDto(kpiReportResults=" + getKpiReportResults() + ")";
  }

  /** Needed to inherit field name constants from {@link ProcessDigestResponseDto} */
  @SuppressWarnings("checkstyle:ConstantName")
  public static class Fields extends ProcessDigestResponseDto.Fields {

    public static final String kpiReportResults = "kpiReportResults";
  }
}
