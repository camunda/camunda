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
import org.camunda.optimize.service.entities.report.ReportImportService;
import org.camunda.optimize.service.identity.IdentityService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportImportService reportImportService;

  public List<IdResponseDto> importEntities(final String userId,
                                            final String collectionId,
                                            final Set<OptimizeEntityExportDto> exportedDtos) {
    validateUserAuthorizedToImportEntitiesOrFail(userId);

    final List<OptimizeEntityExportDto> reportsToImport = exportedDtos.stream()
      .filter(exportDto -> SINGLE_PROCESS_REPORT.equals(exportDto.getExportEntityType())
        || SINGLE_DECISION_REPORT.equals(exportDto.getExportEntityType()))
      .collect(toList());

    final Map<String, IdResponseDto> originalIdToNewIdMap =
      reportImportService.importReportsIntoCollection(userId, collectionId, reportsToImport);

    return new ArrayList<>(originalIdToNewIdMap.values());
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
