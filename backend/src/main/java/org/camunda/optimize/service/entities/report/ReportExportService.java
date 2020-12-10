/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Component
@Slf4j
public class ReportExportService {
  private final ReportReader reportReader;
  private final ReportAuthorizationService reportAuthorizationService;

  public List<ReportDefinitionExportDto> getJsonReportExportDtos(final String userId,
                                                                 final Set<String> reportIds) {
    log.debug("Exporting all reports with IDs {}.", reportIds);
    final List<ReportDefinitionDto<?>> reportDefinitions = retrieveReportDefinitionsOrFailIfMissing(reportIds);
    validateReportAuthorizationsOrFail(userId, reportDefinitions);
    return reportDefinitions.stream().map(this::mapReportDefinitionToExportDto).collect(toList());
  }

  private List<ReportDefinitionDto<?>> retrieveReportDefinitionsOrFailIfMissing(final Set<String> reportIds) {
    final List<String> notFoundReportIds = new ArrayList<>();
    final List<ReportDefinitionDto<?>> reportDefinitions = new ArrayList<>();

    reportIds.forEach(
      reportId -> {
        final List<ReportDefinitionDto<?>> relevantReportDefs =
          retrieveRelevantReportDefinitions(reportId);
        reportDefinitions.addAll(relevantReportDefs);
        if (relevantReportDefs.isEmpty()) {
          notFoundReportIds.add(reportId);
        }
      }
    );

    if (!notFoundReportIds.isEmpty()) {
      throw new NotFoundException("Could not find reports with IDs " + reportIds);
    }

    return reportDefinitions;
  }

  private List<ReportDefinitionDto<?>> retrieveRelevantReportDefinitions(final String reportId) {
    final List<ReportDefinitionDto<?>> reportDefinitions = new ArrayList<>();
    final Optional<ReportDefinitionDto> optionalReportDef = reportReader.getReport(reportId);

    if (optionalReportDef.isPresent()) {
      final ReportDefinitionDto<?> reportDef = optionalReportDef.get();
      reportDefinitions.add(reportDef);
      if (reportDef.isCombined()) {
        final List<String> singleReportIds = ((CombinedReportDefinitionRequestDto) reportDef).getData().getReportIds();
        final List<SingleProcessReportDefinitionRequestDto> singleReportDefs =
          reportReader.getAllSingleProcessReportsForIdsOmitXml(singleReportIds);
        if (singleReportDefs.size() != singleReportIds.size()) {
          throw new OptimizeRuntimeException(
            "Could not retrieve some reports required by combined report with ID " + reportId
          );
        }
        reportDefinitions.addAll(singleReportDefs);
      }
    }

    return reportDefinitions;
  }

  private void validateReportAuthorizationsOrFail(final String userId,
                                                  final List<ReportDefinitionDto<?>> reportDefinitions) {
    List<String> notAuthorizedReportIds = new ArrayList<>();

    reportDefinitions.forEach(reportDef -> {
      if (!reportAuthorizationService.isAuthorizedToReport(userId, reportDef)) {
        notAuthorizedReportIds.add(reportDef.getId());
      }
    });

    if (!notAuthorizedReportIds.isEmpty()) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to access reports with IDs [%s]",
          userId,
          notAuthorizedReportIds
        )
      );
    }
  }

  private ReportDefinitionExportDto mapReportDefinitionToExportDto(final ReportDefinitionDto<?> reportDef) {
    if (ReportType.PROCESS.equals(reportDef.getReportType())) {
      if (reportDef.isCombined()) {
        return new CombinedProcessReportDefinitionExportDto((CombinedReportDefinitionRequestDto) reportDef);
      }
      return new SingleProcessReportDefinitionExportDto((SingleProcessReportDefinitionRequestDto) reportDef);
    } else {
      return new SingleDecisionReportDefinitionExportDto((SingleDecisionReportDefinitionRequestDto) reportDef);
    }
  }
}
