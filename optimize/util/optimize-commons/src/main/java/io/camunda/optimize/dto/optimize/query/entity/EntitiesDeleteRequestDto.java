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
    final int PRIME = 59;
    int result = 1;
    final Object $reports = getReports();
    result = result * PRIME + ($reports == null ? 43 : $reports.hashCode());
    final Object $collections = getCollections();
    result = result * PRIME + ($collections == null ? 43 : $collections.hashCode());
    final Object $dashboards = getDashboards();
    result = result * PRIME + ($dashboards == null ? 43 : $dashboards.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntitiesDeleteRequestDto)) {
      return false;
    }
    final EntitiesDeleteRequestDto other = (EntitiesDeleteRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reports = getReports();
    final Object other$reports = other.getReports();
    if (this$reports == null ? other$reports != null : !this$reports.equals(other$reports)) {
      return false;
    }
    final Object this$collections = getCollections();
    final Object other$collections = other.getCollections();
    if (this$collections == null
        ? other$collections != null
        : !this$collections.equals(other$collections)) {
      return false;
    }
    final Object this$dashboards = getDashboards();
    final Object other$dashboards = other.getDashboards();
    if (this$dashboards == null
        ? other$dashboards != null
        : !this$dashboards.equals(other$dashboards)) {
      return false;
    }
    return true;
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
