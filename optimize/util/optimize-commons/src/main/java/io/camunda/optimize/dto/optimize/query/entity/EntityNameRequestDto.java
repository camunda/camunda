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

  @QueryParam("eventBasedProcessId")
  private String eventBasedProcessId;

  public EntityNameRequestDto(
      final String collectionId,
      final String dashboardId,
      final String reportId,
      final String eventBasedProcessId) {
    this.collectionId = collectionId;
    this.dashboardId = dashboardId;
    this.reportId = reportId;
    this.eventBasedProcessId = eventBasedProcessId;
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

  public String getEventBasedProcessId() {
    return eventBasedProcessId;
  }

  public void setEventBasedProcessId(final String eventBasedProcessId) {
    this.eventBasedProcessId = eventBasedProcessId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EntityNameRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    final Object $dashboardId = getDashboardId();
    result = result * PRIME + ($dashboardId == null ? 43 : $dashboardId.hashCode());
    final Object $reportId = getReportId();
    result = result * PRIME + ($reportId == null ? 43 : $reportId.hashCode());
    final Object $eventBasedProcessId = getEventBasedProcessId();
    result = result * PRIME + ($eventBasedProcessId == null ? 43 : $eventBasedProcessId.hashCode());
    return result;
  }

  @Override
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
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    final Object this$dashboardId = getDashboardId();
    final Object other$dashboardId = other.getDashboardId();
    if (this$dashboardId == null
        ? other$dashboardId != null
        : !this$dashboardId.equals(other$dashboardId)) {
      return false;
    }
    final Object this$reportId = getReportId();
    final Object other$reportId = other.getReportId();
    if (this$reportId == null ? other$reportId != null : !this$reportId.equals(other$reportId)) {
      return false;
    }
    final Object this$eventBasedProcessId = getEventBasedProcessId();
    final Object other$eventBasedProcessId = other.getEventBasedProcessId();
    if (this$eventBasedProcessId == null
        ? other$eventBasedProcessId != null
        : !this$eventBasedProcessId.equals(other$eventBasedProcessId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EntityNameRequestDto(collectionId="
        + getCollectionId()
        + ", dashboardId="
        + getDashboardId()
        + ", reportId="
        + getReportId()
        + ", eventBasedProcessId="
        + getEventBasedProcessId()
        + ")";
  }

  public enum Fields {
    collectionId,
    dashboardId,
    reportId,
    eventBasedProcessId
  }
}
