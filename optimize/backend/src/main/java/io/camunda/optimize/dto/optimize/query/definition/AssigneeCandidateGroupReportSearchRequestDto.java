/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

public class AssigneeCandidateGroupReportSearchRequestDto {

  private String terms;
  private int limit = 25;
  @NotNull @NotEmpty private List<String> reportIds;

  public AssigneeCandidateGroupReportSearchRequestDto(
      final String terms, final int limit, @NotNull @NotEmpty final List<String> reportIds) {
    this.terms = terms;
    this.limit = limit;
    this.reportIds = reportIds;
  }

  public AssigneeCandidateGroupReportSearchRequestDto() {}

  public Optional<String> getTerms() {
    return Optional.ofNullable(terms);
  }

  public void setTerms(final String terms) {
    this.terms = terms;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(final int limit) {
    this.limit = limit;
  }

  public @NotNull @NotEmpty List<String> getReportIds() {
    return reportIds;
  }

  public void setReportIds(@NotNull @NotEmpty final List<String> reportIds) {
    this.reportIds = reportIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AssigneeCandidateGroupReportSearchRequestDto;
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
    return "AssigneeCandidateGroupReportSearchRequestDto(terms="
        + getTerms()
        + ", limit="
        + getLimit()
        + ", reportIds="
        + getReportIds()
        + ")";
  }

  private static int defaultLimit() {
    return 25;
  }

  public static AssigneeCandidateGroupReportSearchRequestDtoBuilder builder() {
    return new AssigneeCandidateGroupReportSearchRequestDtoBuilder();
  }

  public static class AssigneeCandidateGroupReportSearchRequestDtoBuilder {

    private String terms;
    private int limitValue;
    private boolean limitSet;
    private @NotNull @NotEmpty List<String> reportIds;

    AssigneeCandidateGroupReportSearchRequestDtoBuilder() {}

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder terms(final String terms) {
      this.terms = terms;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder limit(final int limit) {
      limitValue = limit;
      limitSet = true;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder reportIds(
        @NotNull @NotEmpty final List<String> reportIds) {
      this.reportIds = reportIds;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDto build() {
      int limitValue = this.limitValue;
      if (!limitSet) {
        limitValue = AssigneeCandidateGroupReportSearchRequestDto.defaultLimit();
      }
      return new AssigneeCandidateGroupReportSearchRequestDto(terms, limitValue, reportIds);
    }

    @Override
    public String toString() {
      return "AssigneeCandidateGroupReportSearchRequestDto.AssigneeCandidateGroupReportSearchRequestDtoBuilder(terms="
          + terms
          + ", limitValue="
          + limitValue
          + ", reportIds="
          + reportIds
          + ")";
    }
  }
}
