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
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $tiles = getTiles();
    result = result * PRIME + ($tiles == null ? 43 : $tiles.hashCode());
    final Object $availableFilters = getAvailableFilters();
    result = result * PRIME + ($availableFilters == null ? 43 : $availableFilters.hashCode());
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    result = result * PRIME + (isInstantPreviewDashboard() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DashboardDefinitionExportDto)) {
      return false;
    }
    final DashboardDefinitionExportDto other = (DashboardDefinitionExportDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$tiles = getTiles();
    final Object other$tiles = other.getTiles();
    if (this$tiles == null ? other$tiles != null : !this$tiles.equals(other$tiles)) {
      return false;
    }
    final Object this$availableFilters = getAvailableFilters();
    final Object other$availableFilters = other.getAvailableFilters();
    if (this$availableFilters == null
        ? other$availableFilters != null
        : !this$availableFilters.equals(other$availableFilters)) {
      return false;
    }
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    if (isInstantPreviewDashboard() != other.isInstantPreviewDashboard()) {
      return false;
    }
    return true;
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

  public static final class Fields {

    public static final String tiles = "tiles";
    public static final String availableFilters = "availableFilters";
    public static final String collectionId = "collectionId";
    public static final String isInstantPreviewDashboard = "isInstantPreviewDashboard";
  }
}
