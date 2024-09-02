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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
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

  public static final class Fields {

    public static final String tiles = "tiles";
    public static final String availableFilters = "availableFilters";
    public static final String collectionId = "collectionId";
    public static final String isInstantPreviewDashboard = "isInstantPreviewDashboard";
  }
}
