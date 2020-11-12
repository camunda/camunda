/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class EntityExportService {

  private final IdentityService identityService;
  private final ReportReader reportReader;
  private final ReportAuthorizationService reportAuthorizationService;

  @SuppressWarnings("unchecked")
  public <T extends ReportDefinitionExportDto> Optional<T> getJsonReportExportDto(final String userId,
                                                                                  final ReportType reportType,
                                                                                  final String reportId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to export reports. Only superusers are authorized to export entities.",
          userId
        )
      );
    }

    switch (reportType) {
      case PROCESS:
        return (Optional<T>) getSingleProcessReportExportDto(userId, reportId);
      case DECISION:
        return (Optional<T>) getSingleDecisionReportExportDto(userId, reportId);
      default:
        throw new IllegalArgumentException("Unknown reportType: " + reportType);
    }
  }

  private Optional<SingleProcessReportDefinitionExportDto> getSingleProcessReportExportDto(final String userId,
                                                                                           final String reportId) {
    log.debug("Exporting report with ID {}.", reportId);

    final Optional<SingleProcessReportDefinitionRequestDto> reportDefinition =
      reportReader.getSingleProcessReportOmitXml(reportId);

    if (!reportDefinition.isPresent()) {
      log.debug("Could not find report with ID {} to export.", reportId);
    }
    reportDefinition.ifPresent(def -> validateReportAuthorizationOrFail(userId, def));

    return reportDefinition.map(SingleProcessReportDefinitionExportDto::new);
  }

  private Optional<SingleDecisionReportDefinitionExportDto> getSingleDecisionReportExportDto(final String userId,
                                                                                             final String reportId) {
    log.debug("Exporting report with ID {}.", reportId);

    final Optional<SingleDecisionReportDefinitionRequestDto> reportDefinition =
      reportReader.getSingleDecisionReportOmitXml(reportId);

    if (!reportDefinition.isPresent()) {
      log.debug("Could not find report with ID {} to export.", reportId);
    }
    reportDefinition.ifPresent(def -> validateReportAuthorizationOrFail(userId, def));

    return reportDefinition.map(SingleDecisionReportDefinitionExportDto::new);
  }

  private void validateReportAuthorizationOrFail(final String userId,
                                                 final ReportDefinitionDto<?> reportDefinition) {
    if (!reportAuthorizationService.isAuthorizedToReport(userId, reportDefinition)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to access report with ID [%s]",
          userId,
          reportDefinition.getId()
        )
      );
    }
  }

}
