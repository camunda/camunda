/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DashboardClient {

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public DashboardDefinitionDto getDashboard(final String dashboardId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  public String createDashboard(String collectionId, List<String> reportIds) {
    return createDashboard(createSimpleDashboardDefinition(collectionId, reportIds));
  }

  private DashboardDefinitionDto createSimpleDashboardDefinition(String collectionId, List<String> reportIds) {
    DashboardDefinitionDto definitionDto = new DashboardDefinitionDto();
    definitionDto.setName("MyAwesomeDashboard");
    definitionDto.setCollectionId(collectionId);
    definitionDto.setReports(
      reportIds.stream()
      .map(reportId -> ReportLocationDto.builder().id(reportId).build())
      .collect(Collectors.toList())
    );
    return definitionDto;
  }

  private String createDashboard(final DashboardDefinitionDto dashboardDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }
}
