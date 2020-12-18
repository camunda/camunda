/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.entities.dashboard.DashboardExportService;
import org.camunda.optimize.service.entities.report.ReportExportService;
import org.camunda.optimize.service.identity.IdentityService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class EntityExportService {

  private final IdentityService identityService;
  private final ReportExportService reportExportService;
  private final DashboardExportService dashboardExportService;

  public List<ReportDefinitionExportDto> getReportExportDtos(final String userId,
                                                             final Set<String> reportIds) {
    validateUserAuthorizedToExportOrFail(userId);
    return reportExportService.getReportExportDtos(userId, reportIds);
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExport(final String userId,
                                                                  final Set<String> reportIds) {
    validateUserAuthorizedToExportOrFail(userId);
    return dashboardExportService.getCompleteDashboardExport(userId, reportIds);
  }

  private void validateUserAuthorizedToExportOrFail(final String userId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to export reports. Only superusers are authorized to export entities.",
          userId
        )
      );
    }
  }
}
