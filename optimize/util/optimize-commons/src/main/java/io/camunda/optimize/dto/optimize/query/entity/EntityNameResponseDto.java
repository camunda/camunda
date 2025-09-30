/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import java.util.Objects;

public class EntityNameResponseDto {

  private String collectionName;
  private String dashboardName;
  private String reportName;

  public EntityNameResponseDto(
      final String collectionName, final String dashboardName, final String reportName) {
    this.collectionName = collectionName;
    this.dashboardName = dashboardName;
    this.reportName = reportName;
  }

  public EntityNameResponseDto() {}

  public String getCollectionName() {
    return collectionName;
  }

  public void setCollectionName(final String collectionName) {
    this.collectionName = collectionName;
  }

  public String getDashboardName() {
    return dashboardName;
  }

  public void setDashboardName(final String dashboardName) {
    this.dashboardName = dashboardName;
  }

  public String getReportName() {
    return reportName;
  }

  public void setReportName(final String reportName) {
    this.reportName = reportName;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityNameResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EntityNameResponseDto that = (EntityNameResponseDto) o;
    return Objects.equals(collectionName, that.collectionName)
        && Objects.equals(dashboardName, that.dashboardName)
        && Objects.equals(reportName, that.reportName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(collectionName, dashboardName, reportName);
  }

  @Override
  public String toString() {
    return "EntityNameResponseDto(collectionName="
        + getCollectionName()
        + ", dashboardName="
        + getDashboardName()
        + ", reportName="
        + getReportName()
        + ")";
  }
}
