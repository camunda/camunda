/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
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

  public enum Fields {
    collectionId,
    dashboardId,
    reportId
  }
}
