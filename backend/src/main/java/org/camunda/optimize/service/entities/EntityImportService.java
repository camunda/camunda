/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.report.ReportImportService;
import org.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import org.camunda.optimize.service.identity.IdentityService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportImportService reportImportService;
  private final AuthorizedCollectionService collectionService;

  public List<IdResponseDto> importEntities(final String userId,
                                            final String collectionId,
                                            final Set<OptimizeEntityExportDto> entitiesToImport) {
    validateUserAuthorizedToImportEntitiesOrFail(userId);
    validateCollectionAccessOrFail(userId, collectionId);
    validateCompletenessOrFail(entitiesToImport);

    final List<OptimizeEntityExportDto> reportsToImport = entitiesToImport.stream()
      .filter(exportDto -> SINGLE_PROCESS_REPORT.equals(exportDto.getExportEntityType())
        || SINGLE_DECISION_REPORT.equals(exportDto.getExportEntityType())
        || COMBINED.equals(exportDto.getExportEntityType()))
      .collect(toList());

    reportImportService.validateAllReportsOrFail(userId, collectionId, reportsToImport);

    final Map<String, IdResponseDto> originalIdToNewIdMap =
      reportImportService.importReportsIntoCollection(userId, collectionId, reportsToImport);

    return new ArrayList<>(originalIdToNewIdMap.values());
  }

  private void validateCollectionAccessOrFail(final String userId,
                                              final String collectionId) {
    if (collectionId != null) {
      collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);
    }
  }

  private void validateCompletenessOrFail(final Set<OptimizeEntityExportDto> entitiesToImport) {
    final Set<String> importEntityIds =
      entitiesToImport.stream().map(OptimizeEntityExportDto::getId).collect(toSet());

    entitiesToImport.forEach(entity -> {
      if (COMBINED.equals(entity.getExportEntityType())) {
        final List<String> requiredSingleReports =
          ((CombinedProcessReportDefinitionExportDto) entity).getData().getReportIds();
        if (!importEntityIds.containsAll(requiredSingleReports)) {
          throw new OptimizeImportFileInvalidException(
            "Could not import entities because the file is incomplete, some single reports are missing."
          );
        }
      }
    });
  }

  private void validateUserAuthorizedToImportEntitiesOrFail(final String userId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to import entities. Only superusers are authorized to import entities.",
          userId
        )
      );
    }
  }

}
