/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.export.dashboard;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.service.db.schema.index.DashboardIndex;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DashboardDefinitionExportDto extends OptimizeEntityExportDto {

  @NotNull private List<DashboardReportTileDto> tiles = new ArrayList<>();
  @NotNull private List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  private String collectionId;
  private boolean isInstantPreviewDashboard = false;

  public DashboardDefinitionExportDto(final DashboardDefinitionRestDto dashboardDefinition) {
    super(
        dashboardDefinition.getId(),
        ExportEntityType.DASHBOARD,
        dashboardDefinition.getName(),
        dashboardDefinition.getDescription(),
        DashboardIndex.VERSION);
    tiles = dashboardDefinition.getTiles();
    availableFilters = dashboardDefinition.getAvailableFilters();
    collectionId = dashboardDefinition.getCollectionId();
  }

  public DashboardDefinitionExportDto() {}

  @JsonIgnore
  public Set<String> getTileIds() {
    return tiles.stream()
        .map(DashboardReportTileDto::getId)
        .filter(IdGenerator::isValidId)
        .collect(toSet());
  }

  @JsonIgnore
  public Set<String> getExternalResourceUrls() {
    return tiles.stream()
        .map(DashboardReportTileDto::getId)
        .filter(id -> !IdGenerator.isValidId(id))
        .collect(toSet());
  }

  public @NotNull List<DashboardReportTileDto> getTiles() {
    return tiles;
  }

  public void setTiles(@NotNull final List<DashboardReportTileDto> tiles) {
    this.tiles = tiles;
  }

  public @NotNull List<DashboardFilterDto<?>> getAvailableFilters() {
    return availableFilters;
  }

  public void setAvailableFilters(@NotNull final List<DashboardFilterDto<?>> availableFilters) {
    this.availableFilters = availableFilters;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  public boolean isInstantPreviewDashboard() {
    return isInstantPreviewDashboard;
  }

  public void setInstantPreviewDashboard(final boolean isInstantPreviewDashboard) {
    this.isInstantPreviewDashboard = isInstantPreviewDashboard;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DashboardDefinitionExportDto;
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
    return "DashboardDefinitionExportDto(tiles="
        + getTiles()
        + ", availableFilters="
        + getAvailableFilters()
        + ", collectionId="
        + getCollectionId()
        + ", isInstantPreviewDashboard="
        + isInstantPreviewDashboard()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String tiles = "tiles";
    public static final String availableFilters = "availableFilters";
    public static final String collectionId = "collectionId";
    public static final String isInstantPreviewDashboard = "isInstantPreviewDashboard";
  }
}
