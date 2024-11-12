/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class EntitiesDeleteRequestDto {

  @NotNull List<String> reports;
  @NotNull List<String> collections;
  @NotNull List<String> dashboards;

  public EntitiesDeleteRequestDto(
      @NotNull final List<String> reports,
      @NotNull final List<String> collections,
      @NotNull final List<String> dashboards) {
    this.reports = reports;
    this.collections = collections;
    this.dashboards = dashboards;
  }

  public EntitiesDeleteRequestDto() {}

  public @NotNull List<String> getReports() {
    return reports;
  }

  public void setReports(@NotNull final List<String> reports) {
    this.reports = reports;
  }

  public @NotNull List<String> getCollections() {
    return collections;
  }

  public void setCollections(@NotNull final List<String> collections) {
    this.collections = collections;
  }

  public @NotNull List<String> getDashboards() {
    return dashboards;
  }

  public void setDashboards(@NotNull final List<String> dashboards) {
    this.dashboards = dashboards;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntitiesDeleteRequestDto;
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
    return "EntitiesDeleteRequestDto(reports="
        + getReports()
        + ", collections="
        + getCollections()
        + ", dashboards="
        + getDashboards()
        + ")";
  }
}
