/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class DashboardReportTileDto {

  protected String id;
  protected PositionDto position;
  protected DimensionDto dimensions;
  @NotNull protected DashboardTileType type;
  protected Object configuration;

  public DashboardReportTileDto(
      String id,
      PositionDto position,
      DimensionDto dimensions,
      @NotNull DashboardTileType type,
      Object configuration) {
    this.id = id;
    this.position = position;
    this.dimensions = dimensions;
    this.type = type;
    this.configuration = configuration;
  }

  public DashboardReportTileDto() {}

  public static final class Fields {

    public static final String id = "id";
    public static final String position = "position";
    public static final String dimensions = "dimensions";
    public static final String type = "type";
    public static final String configuration = "configuration";
  }
}
