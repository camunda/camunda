/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import jakarta.ws.rs.QueryParam;

public class EntityNameRequestDto {

  @QueryParam("collectionId")
  private String collectionId;

  @QueryParam("dashboardId")
  private String dashboardId;

  @QueryParam("reportId")
  private String reportId;

  public EntityNameRequestDto(String collectionId, String dashboardId, String reportId) {
    this.collectionId = collectionId;
    this.dashboardId = dashboardId;
    this.reportId = reportId;
  }

  public EntityNameRequestDto() {}

  public String getCollectionId() {
    return this.collectionId;
  }

  public void setCollectionId(String collectionId) {
    this.collectionId = collectionId;
  }

  public String getDashboardId() {
    return this.dashboardId;
  }

  public void setDashboardId(String dashboardId) {
    this.dashboardId = dashboardId;
  }

  public String getReportId() {
    return this.reportId;
  }

  public void setReportId(String reportId) {
    this.reportId = reportId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityNameRequestDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $collectionId = this.getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    final Object $dashboardId = this.getDashboardId();
    result = result * PRIME + ($dashboardId == null ? 43 : $dashboardId.hashCode());
    final Object $reportId = this.getReportId();
    result = result * PRIME + ($reportId == null ? 43 : $reportId.hashCode());
    return result;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EntityNameRequestDto)) {
      return false;
    }
    final EntityNameRequestDto other = (EntityNameRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$collectionId = this.getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    final Object this$dashboardId = this.getDashboardId();
    final Object other$dashboardId = other.getDashboardId();
    if (this$dashboardId == null
        ? other$dashboardId != null
        : !this$dashboardId.equals(other$dashboardId)) {
      return false;
    }
    final Object this$reportId = this.getReportId();
    final Object other$reportId = other.getReportId();
    if (this$reportId == null ? other$reportId != null : !this$reportId.equals(other$reportId)) {
      return false;
    }
    return true;
  }

  public String toString() {
    return "EntityNameRequestDto(collectionId="
        + this.getCollectionId()
        + ", dashboardId="
        + this.getDashboardId()
        + ", reportId="
        + this.getReportId()
        + ")";
  }

  public enum Fields {
    collectionId,
    dashboardId,
    reportId
  }
}
