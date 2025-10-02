/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import java.util.Objects;

public class EntityNameRequestDto {

  private String collectionId;

  private String dashboardId;

  private String reportId;

  public EntityNameRequestDto(
      final String collectionId, final String dashboardId, final String reportId) {
    this.collectionId = collectionId;
    this.dashboardId = dashboardId;
    this.reportId = reportId;
  }

  public EntityNameRequestDto() {}

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  public String getDashboardId() {
    return dashboardId;
  }

  public void setDashboardId(final String dashboardId) {
    this.dashboardId = dashboardId;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityNameRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EntityNameRequestDto that = (EntityNameRequestDto) o;
    return Objects.equals(collectionId, that.collectionId)
        && Objects.equals(dashboardId, that.dashboardId)
        && Objects.equals(reportId, that.reportId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(collectionId, dashboardId, reportId);
  }

  @Override
  public String toString() {
    return "EntityNameRequestDto(collectionId="
        + getCollectionId()
        + ", dashboardId="
        + getDashboardId()
        + ", reportId="
        + getReportId()
        + ")";
  }

  public enum Fields {
    collectionId,
    dashboardId,
    reportId
  }
}
