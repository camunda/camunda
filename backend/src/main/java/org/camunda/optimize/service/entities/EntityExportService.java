/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.entities.dashboard.DashboardExportService;
import org.camunda.optimize.service.entities.report.ReportExportService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Component
@Slf4j
public class EntityExportService {

  private final AbstractIdentityService identityService;
  private final ReportExportService reportExportService;
  private final DashboardExportService dashboardExportService;

  public List<ReportDefinitionExportDto> getReportExportDtos(final Set<String> reportIds) {
    return reportExportService.getReportExportDtos(reportIds);
  }

  public List<ReportDefinitionExportDto> getReportExportDtosAsUser(final String userId,
                                                                   final Set<String> reportIds) {
    validateUserAuthorizedToExportOrFail(userId);
    return reportExportService.getReportExportDtosAsUser(userId, reportIds);
  }

  public List<OptimizeEntityExportDto> getDashboardExportDtos(final Set<String> dashboardIds) {
    return dashboardExportService.getCompleteDashboardExport(dashboardIds);
  }

  public List<OptimizeEntityExportDto> getCompleteDashboardExportAsUser(final String userId,
                                                                        final Set<String> dashboardIds) {
    validateUserAuthorizedToExportOrFail(userId);
    return dashboardExportService.getCompleteDashboardExport(userId, dashboardIds);
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
