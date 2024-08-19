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
    final int PRIME = 59;
    int result = 1;
    final Object $terms = getTerms();
    result = result * PRIME + ($terms == null ? 43 : $terms.hashCode());
    result = result * PRIME + getLimit();
    final Object $reportIds = getReportIds();
    result = result * PRIME + ($reportIds == null ? 43 : $reportIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AssigneeCandidateGroupReportSearchRequestDto)) {
      return false;
    }
    final AssigneeCandidateGroupReportSearchRequestDto other =
        (AssigneeCandidateGroupReportSearchRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$terms = getTerms();
    final Object other$terms = other.getTerms();
    if (this$terms == null ? other$terms != null : !this$terms.equals(other$terms)) {
      return false;
    }
    if (getLimit() != other.getLimit()) {
      return false;
    }
    final Object this$reportIds = getReportIds();
    final Object other$reportIds = other.getReportIds();
    if (this$reportIds == null
        ? other$reportIds != null
        : !this$reportIds.equals(other$reportIds)) {
      return false;
    }
    return true;
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

  private static int $default$limit() {
    return 25;
  }

  public static AssigneeCandidateGroupReportSearchRequestDtoBuilder builder() {
    return new AssigneeCandidateGroupReportSearchRequestDtoBuilder();
  }

  public static class AssigneeCandidateGroupReportSearchRequestDtoBuilder {

    private String terms;
    private int limit$value;
    private boolean limit$set;
    private @NotNull @NotEmpty List<String> reportIds;

    AssigneeCandidateGroupReportSearchRequestDtoBuilder() {}

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder terms(final String terms) {
      this.terms = terms;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder limit(final int limit) {
      limit$value = limit;
      limit$set = true;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDtoBuilder reportIds(
        @NotNull @NotEmpty final List<String> reportIds) {
      this.reportIds = reportIds;
      return this;
    }

    public AssigneeCandidateGroupReportSearchRequestDto build() {
      int limit$value = this.limit$value;
      if (!limit$set) {
        limit$value = AssigneeCandidateGroupReportSearchRequestDto.$default$limit();
      }
      return new AssigneeCandidateGroupReportSearchRequestDto(terms, limit$value, reportIds);
    }

    @Override
    public String toString() {
      return "AssigneeCandidateGroupReportSearchRequestDto.AssigneeCandidateGroupReportSearchRequestDtoBuilder(terms="
          + terms
          + ", limit$value="
          + limit$value
          + ", reportIds="
          + reportIds
          + ")";
    }
  }
}
