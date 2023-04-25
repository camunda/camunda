/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.util.IdGenerator;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@NoArgsConstructor
@FieldNameConstants
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardDefinitionExportDto extends OptimizeEntityExportDto {
  @NotNull
  private List<DashboardReportTileDto> tiles = new ArrayList<>();
  @NotNull
  private List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  private String collectionId;
  private boolean isInstantPreviewDashboard = false;

  public DashboardDefinitionExportDto(final DashboardDefinitionRestDto dashboardDefinition) {
    super(
      dashboardDefinition.getId(),
      ExportEntityType.DASHBOARD,
      dashboardDefinition.getName(),
      null, // TODO: Dashboard descriptions will be handled in https://jira.camunda.com/browse/OPT-6885
      DashboardIndex.VERSION
    );
    this.tiles = dashboardDefinition.getTiles();
    this.availableFilters = dashboardDefinition.getAvailableFilters();
    this.collectionId = dashboardDefinition.getCollectionId();
  }

  @JsonIgnore
  public Set<String> getTileIds() {
    return tiles.stream().map(DashboardReportTileDto::getId).filter(IdGenerator::isValidId).collect(toSet());
  }

  @JsonIgnore
  public Set<String> getExternalResourceUrls() {
    return tiles.stream().map(DashboardReportTileDto::getId).filter(id -> !IdGenerator.isValidId(id)).collect(toSet());
  }
}
