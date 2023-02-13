/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
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
  private final AuthorizedCollectionService authorizedCollectionService;

  public List<ReportDefinitionExportDto> getReportExportDtos(final Set<String> reportIds) {
    log.debug("Exporting all reports with IDs {} for export via API.", reportIds);
    final List<ReportDefinitionDto<?>> reportDefinitions = retrieveReportDefinitionsOrFailIfMissing(reportIds);
    return reportDefinitions.stream().map(ReportDefinitionExportDto::mapReportDefinitionToExportDto).collect(toList());
  }

  public List<ReportDefinitionExportDto> getReportExportDtosAsUser(final String userId,
                                                                   final Set<String> reportIds) {
    log.debug("Exporting all reports with IDs {} as user {}.", reportIds, userId);
    final List<ReportDefinitionDto<?>> reportDefinitions = retrieveReportDefinitionsOrFailIfMissing(reportIds);
    validateReportAuthorizationsOrFail(userId, reportDefinitions);
    return reportDefinitions.stream().map(ReportDefinitionExportDto::mapReportDefinitionToExportDto).collect(toList());
  }

  public List<ReportDefinitionDto<?>> retrieveReportDefinitionsOrFailIfMissing(final Set<String> reportIds) {
    final Set<String> notFoundReportIds = Sets.newHashSet();
    final Set<String> exportedReportIds = Sets.newHashSet();
    final List<ReportDefinitionDto<?>> reportDefinitions = new ArrayList<>();

    reportIds.forEach(
      reportId -> {
        reportDefinitions.addAll(
          retrieveRelevantReportDefinitionsOrFailIfMissing(reportId, exportedReportIds)
        );
        if (!exportedReportIds.contains(reportId)) {
          notFoundReportIds.add(reportId);
        }
      }
    );

    if (!notFoundReportIds.isEmpty()) {
      throw new NotFoundException("Could not find reports with IDs " + reportIds);
    }

    return reportDefinitions;
  }

  private List<ReportDefinitionDto<?>> retrieveRelevantReportDefinitionsOrFailIfMissing(final String reportIdToExport,
                                                                                        final Set<String> alreadyExportedIds) {
    if (alreadyExportedIds.contains(reportIdToExport)) {
      return Collections.emptyList();
    }

    final List<ReportDefinitionDto<?>> reportDefinitions = new ArrayList<>();
    final Optional<ReportDefinitionDto> optionalReportDef = reportReader.getReport(reportIdToExport);

    if (optionalReportDef.isPresent()) {
      final ReportDefinitionDto<?> reportDef = optionalReportDef.get();
      reportDefinitions.add(reportDef);
      alreadyExportedIds.add(reportDef.getId());
      if (reportDef.isCombined()) {
        final List<String> singleReportIds = ((CombinedReportDefinitionRequestDto) reportDef).getData().getReportIds();
        singleReportIds.removeAll(alreadyExportedIds);
        final List<SingleProcessReportDefinitionRequestDto> singleReportDefs =
          reportReader.getAllSingleProcessReportsForIdsOmitXml(singleReportIds);
        if (singleReportDefs.size() != singleReportIds.size()) {
          throw new OptimizeRuntimeException(
            "Could not retrieve some reports required by combined report with ID " + reportIdToExport
          );
        }
        reportDefinitions.addAll(singleReportDefs);
        alreadyExportedIds.addAll(singleReportIds);
      }
    }

    return reportDefinitions;
  }

  public void validateReportAuthorizationsOrFail(final String userId,
                                                 final List<ReportDefinitionDto<?>> reportDefinitions) {
    List<String> notAuthorizedReportIds = new ArrayList<>();

    reportDefinitions.forEach(reportDef -> {
      if (!reportAuthorizationService.isAuthorizedToReport(userId, reportDef)) {
        notAuthorizedReportIds.add(reportDef.getId());
      }
      Optional.ofNullable(reportDef.getCollectionId())
        .ifPresent(collectionId -> authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
          userId,
          collectionId
        ));
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

}
