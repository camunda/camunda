/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities;

import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.service.entities.dashboard.DashboardExportService;
import io.camunda.optimize.service.entities.report.ReportExportService;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class EntityExportService {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(EntityExportService.class);
  private final ReportExportService reportExportService;
  private final DashboardExportService dashboardExportService;

  public EntityExportService(
      final ReportExportService reportExportService,
      final DashboardExportService dashboardExportService) {
    this.reportExportService = reportExportService;
    this.dashboardExportService = dashboardExportService;
  }

  public List<ReportDefinitionExportDto> getReportExportDtos(final Set<String> reportIds) {
    return reportExportService.getReportExportDtos(reportIds);
  }

  public List<ReportDefinitionExportDto> getReportExportDtosAsUser(
      final String userId, final Set<String> reportIds) {
    return reportExportService.getReportExportDtosAsUser(userId, reportIds);
  }

  public List<OptimizeEntityExportDto> getDashboardExportDtos(final Set<String> dashboardIds) {
    return dashboardExportService.getCompleteDashboardExport(dashboardIds);
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExportAsUser(
      final String userId, final String dashboardId) {
    return dashboardExportService.getCompleteDashboardExport(userId, dashboardId);
  }
}
