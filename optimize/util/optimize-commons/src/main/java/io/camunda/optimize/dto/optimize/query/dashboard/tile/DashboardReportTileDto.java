/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

import jakarta.validation.constraints.NotNull;

public class DashboardReportTileDto {

  protected String id;
  protected PositionDto position;
  protected DimensionDto dimensions;
  @NotNull protected DashboardTileType type;
  protected Object configuration;

  public DashboardReportTileDto(
      final String id,
      final PositionDto position,
      final DimensionDto dimensions,
      @NotNull final DashboardTileType type,
      final Object configuration) {
    this.id = id;
    this.position = position;
    this.dimensions = dimensions;
    this.type = type;
    this.configuration = configuration;
  }

  public DashboardReportTileDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public PositionDto getPosition() {
    return position;
  }

  public void setPosition(final PositionDto position) {
    this.position = position;
  }

  public DimensionDto getDimensions() {
    return dimensions;
  }

  public void setDimensions(final DimensionDto dimensions) {
    this.dimensions = dimensions;
  }

  public @NotNull DashboardTileType getType() {
    return type;
  }

  public void setType(@NotNull final DashboardTileType type) {
    this.type = type;
  }

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final Object configuration) {
    this.configuration = configuration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DashboardReportTileDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $position = getPosition();
    result = result * PRIME + ($position == null ? 43 : $position.hashCode());
    final Object $dimensions = getDimensions();
    result = result * PRIME + ($dimensions == null ? 43 : $dimensions.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $configuration = getConfiguration();
    result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardReportTileDto)) {
      return false;
    }
    final DashboardReportTileDto other = (DashboardReportTileDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$position = getPosition();
    final Object other$position = other.getPosition();
    if (this$position == null ? other$position != null : !this$position.equals(other$position)) {
      return false;
    }
    final Object this$dimensions = getDimensions();
    final Object other$dimensions = other.getDimensions();
    if (this$dimensions == null
        ? other$dimensions != null
        : !this$dimensions.equals(other$dimensions)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$configuration = getConfiguration();
    final Object other$configuration = other.getConfiguration();
    if (this$configuration == null
        ? other$configuration != null
        : !this$configuration.equals(other$configuration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DashboardReportTileDto(id="
        + getId()
        + ", position="
        + getPosition()
        + ", dimensions="
        + getDimensions()
        + ", type="
        + getType()
        + ", configuration="
        + getConfiguration()
        + ")";
  }

  public static DashboardReportTileDtoBuilder builder() {
    return new DashboardReportTileDtoBuilder();
  }

  public DashboardReportTileDtoBuilder toBuilder() {
    return new DashboardReportTileDtoBuilder()
        .id(id)
        .position(position)
        .dimensions(dimensions)
        .type(type)
        .configuration(configuration);
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String position = "position";
    public static final String dimensions = "dimensions";
    public static final String type = "type";
    public static final String configuration = "configuration";
  }

  public static class DashboardReportTileDtoBuilder {

    private String id;
    private PositionDto position;
    private DimensionDto dimensions;
    private @NotNull DashboardTileType type;
    private Object configuration;

    DashboardReportTileDtoBuilder() {}

    public DashboardReportTileDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public DashboardReportTileDtoBuilder position(final PositionDto position) {
      this.position = position;
      return this;
    }

    public DashboardReportTileDtoBuilder dimensions(final DimensionDto dimensions) {
      this.dimensions = dimensions;
      return this;
    }

    public DashboardReportTileDtoBuilder type(@NotNull final DashboardTileType type) {
      this.type = type;
      return this;
    }

    public DashboardReportTileDtoBuilder configuration(final Object configuration) {
      this.configuration = configuration;
      return this;
    }

    public DashboardReportTileDto build() {
      return new DashboardReportTileDto(id, position, dimensions, type, configuration);
    }

    @Override
    public String toString() {
      return "DashboardReportTileDto.DashboardReportTileDtoBuilder(id="
          + id
          + ", position="
          + position
          + ", dimensions="
          + dimensions
          + ", type="
          + type
          + ", configuration="
          + configuration
          + ")";
    }
  }
}
