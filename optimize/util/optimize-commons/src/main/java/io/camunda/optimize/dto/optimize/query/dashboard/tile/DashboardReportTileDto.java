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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
