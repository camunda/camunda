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
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.entities.report.ReportImportService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;

import static org.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportImportService reportImportService;

  public IdResponseDto importEntity(final String userId,
                                    final String collectionId,
                                    final OptimizeEntityExportDto exportedDto) {
    validateUserAuthorizedToImportEntitiesOrFail(userId);

    switch (exportedDto.getExportEntityType()) {
      case SINGLE_DECISION_REPORT:
        return reportImportService.importDecisionReportIntoCollection(
          userId,
          normalizeNullStringValue(collectionId),
          (SingleDecisionReportDefinitionExportDto) exportedDto
        );
      case SINGLE_PROCESS_REPORT:
        return reportImportService.importProcessReportIntoCollection(
          userId,
          normalizeNullStringValue(collectionId),
          (SingleProcessReportDefinitionExportDto) exportedDto
        );
      default:
        throw new OptimizeRuntimeException("Unknown entity type: " + exportedDto.getExportEntityType());
    }
  }

  private void validateUserAuthorizedToImportEntitiesOrFail(final String userId) {
    if (!identityService.isSuperUserIdentity(userId)) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to import reports. Only superusers are authorized to import entities.",
          userId
        )
      );
    }
  }

}
