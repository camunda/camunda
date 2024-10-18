/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $collectionName = getCollectionName();
    result = result * PRIME + ($collectionName == null ? 43 : $collectionName.hashCode());
    final Object $dashboardName = getDashboardName();
    result = result * PRIME + ($dashboardName == null ? 43 : $dashboardName.hashCode());
    final Object $reportName = getReportName();
    result = result * PRIME + ($reportName == null ? 43 : $reportName.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityNameResponseDto)) {
      return false;
    }
    final EntityNameResponseDto other = (EntityNameResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$collectionName = getCollectionName();
    final Object other$collectionName = other.getCollectionName();
    if (this$collectionName == null
        ? other$collectionName != null
        : !this$collectionName.equals(other$collectionName)) {
      return false;
    }
    final Object this$dashboardName = getDashboardName();
    final Object other$dashboardName = other.getDashboardName();
    if (this$dashboardName == null
        ? other$dashboardName != null
        : !this$dashboardName.equals(other$dashboardName)) {
      return false;
    }
    final Object this$reportName = getReportName();
    final Object other$reportName = other.getReportName();
    if (this$reportName == null
        ? other$reportName != null
        : !this$reportName.equals(other$reportName)) {
      return false;
    }
    return true;
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
