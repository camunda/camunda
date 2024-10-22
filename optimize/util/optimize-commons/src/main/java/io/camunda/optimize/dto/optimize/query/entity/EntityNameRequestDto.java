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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
