/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export.dashboard;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@FieldNameConstants
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardDefinitionExportDto extends OptimizeEntityExportDto {
  @NotNull
  private List<ReportLocationDto> reports = new ArrayList<>();
  @NotNull
  private List<DashboardFilterDto> availableFilters = new ArrayList<>();
  private String collectionId;

  public DashboardDefinitionExportDto(final DashboardDefinitionRestDto dashboardDefinition) {
    super(
      dashboardDefinition.getId(),
      ExportEntityType.DASHBOARD,
      dashboardDefinition.getName(),
      DashboardIndex.VERSION
      );
    this.reports = dashboardDefinition.getReports();
    this.availableFilters = dashboardDefinition.getAvailableFilters();
    this.collectionId = dashboardDefinition.getCollectionId();
  }
}
