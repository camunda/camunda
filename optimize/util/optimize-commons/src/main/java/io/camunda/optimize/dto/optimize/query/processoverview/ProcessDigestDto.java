/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import java.util.Map;

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
  public String toString() {
    return "ProcessDigestDto(kpiReportResults=" + getKpiReportResults() + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessDigestDto)) {
      return false;
    }
    final ProcessDigestDto other = (ProcessDigestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$kpiReportResults = getKpiReportResults();
    final Object other$kpiReportResults = other.getKpiReportResults();
    if (this$kpiReportResults == null
        ? other$kpiReportResults != null
        : !this$kpiReportResults.equals(other$kpiReportResults)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDigestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $kpiReportResults = getKpiReportResults();
    result = result * PRIME + ($kpiReportResults == null ? 43 : $kpiReportResults.hashCode());
    return result;
  }

  /** Needed to inherit field name constants from {@link ProcessDigestResponseDto} */
  public static class Fields extends ProcessDigestResponseDto.Fields {

    public static final String kpiReportResults = "kpiReportResults";
  }
}
