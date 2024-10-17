/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AssigneeCandidateGroupDefinitionSearchRequestDto {

  private String terms;
  private int limit = 25;
  @NotNull private String processDefinitionKey;

  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));

  public AssigneeCandidateGroupDefinitionSearchRequestDto(
      final String terms,
      final int limit,
      @NotNull final String processDefinitionKey,
      final List<String> tenantIds) {
    this.terms = terms;
    this.limit = limit;
    this.processDefinitionKey = processDefinitionKey;
    this.tenantIds = tenantIds;
  }

  public AssigneeCandidateGroupDefinitionSearchRequestDto() {}

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

  public @NotNull String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(@NotNull final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AssigneeCandidateGroupDefinitionSearchRequestDto;
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
    return "AssigneeCandidateGroupDefinitionSearchRequestDto(terms="
        + getTerms()
        + ", limit="
        + getLimit()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", tenantIds="
        + getTenantIds()
        + ")";
  }

  private static int $default$limit() {
    return 25;
  }

  private static List<String> $default$tenantIds() {
    return new ArrayList<>(Collections.singletonList(null));
  }

  public static AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder builder() {
    return new AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder();
  }

  public static class AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder {

    private String terms;
    private int limit$value;
    private boolean limit$set;
    private @NotNull String processDefinitionKey;
    private List<String> tenantIds$value;
    private boolean tenantIds$set;

    AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder() {}

    public AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder terms(final String terms) {
      this.terms = terms;
      return this;
    }

    public AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder limit(final int limit) {
      limit$value = limit;
      limit$set = true;
      return this;
    }

    public AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder processDefinitionKey(
        @NotNull final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder tenantIds(
        final List<String> tenantIds) {
      tenantIds$value = tenantIds;
      tenantIds$set = true;
      return this;
    }

    public AssigneeCandidateGroupDefinitionSearchRequestDto build() {
      int limit$value = this.limit$value;
      if (!limit$set) {
        limit$value = AssigneeCandidateGroupDefinitionSearchRequestDto.$default$limit();
      }
      List<String> tenantIds$value = this.tenantIds$value;
      if (!tenantIds$set) {
        tenantIds$value = AssigneeCandidateGroupDefinitionSearchRequestDto.$default$tenantIds();
      }
      return new AssigneeCandidateGroupDefinitionSearchRequestDto(
          terms, limit$value, processDefinitionKey, tenantIds$value);
    }

    @Override
    public String toString() {
      return "AssigneeCandidateGroupDefinitionSearchRequestDto.AssigneeCandidateGroupDefinitionSearchRequestDtoBuilder(terms="
          + terms
          + ", limit$value="
          + limit$value
          + ", processDefinitionKey="
          + processDefinitionKey
          + ", tenantIds$value="
          + tenantIds$value
          + ")";
    }
  }
}
