/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportService reportService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;

  public IdResponseDto importProcessReportIntoCollection(final String userId,
                                                         final String collectionId,
                                                         final SingleProcessReportDefinitionExportDto exportedDto) {
    validateIndexVersionOrFail(exportedDto);
    validateUserAuthorizedToImportEntitiesOrFail(userId);
    prepareVersionListForImportOrFailIfNoneExists(exportedDto);
    validateAuthorizedToAccessDefinitionOrFail(
      userId,
      DefinitionType.PROCESS,
      exportedDto.getData().getProcessDefinitionKey(),
      exportedDto.getData().getTenantIds()
    );

    return reportService.importReport(
      userId,
      createProcessReportDefinition(exportedDto),
      collectionId
    );
  }

  private SingleProcessReportDefinitionRequestDto createProcessReportDefinition(
    final SingleProcessReportDefinitionExportDto exportedDto) {
    final SingleProcessReportDefinitionRequestDto reportDefinition =
      new SingleProcessReportDefinitionRequestDto(exportedDto.getData());
    reportDefinition.setName(exportedDto.getName());
    reportDefinition.setCreated(OffsetDateTime.now());
    return reportDefinition;
  }

  private void prepareVersionListForImportOrFailIfNoneExists(final SingleProcessReportDefinitionExportDto exportDto) {
    final List<String> defVersions = getExistingDefinitionVersions(
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getTenantIds()
    );
    exportDto.getData()
      .getProcessDefinitionVersions()
      .removeIf(version -> !defVersions.contains(version));
    if (exportDto.getData().getDefinitionVersions().isEmpty()) {
      final List<String> tenantIds = exportDto.getData().getTenantIds();
      final String tenantString = tenantIds.isEmpty() || tenantIds.stream().allMatch(Objects::isNull)
        ? "for shared tenant"
        : "for tenants " + tenantIds;
      throw new NotFoundException(
        String.format(
          "Could not import report because no process definition with key %s exists %s.",
          exportDto.getData().getProcessDefinitionKey(),
          tenantString
        ));
    }
  }

  private List<String> getExistingDefinitionVersions(final DefinitionType definitionType,
                                                     final String definitionKey,
                                                     final List<String> tenantIds) {
    return definitionService.getDefinitionVersions(
      definitionType,
      definitionKey,
      tenantIds
    ).stream()
      .map(DefinitionVersionResponseDto::getVersion)
      .collect(toList());
  }

  private void validateIndexVersionOrFail(final SingleProcessReportDefinitionExportDto exportDto) {
    if (SingleProcessReportIndex.VERSION != exportDto.getSourceIndexVersion()) {
      throw new BadRequestException(
        String.format(
          "Could not import report because the source and target report data structure does not match. " +
            "Source index version: %d. Target index version: %d",
          exportDto.getSourceIndexVersion(),
          SingleProcessReportIndex.VERSION
        ));
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

  private void validateAuthorizedToAccessDefinitionOrFail(final String userId,
                                                          final DefinitionType definitionType,
                                                          final String definitionKey,
                                                          final List<String> tenantIds) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId,
      definitionType,
      definitionKey,
      tenantIds
    )) {
      throw new ForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to access the definition with key [%s] and tenants %s the imported " +
            "report is based on.",
          userId,
          definitionKey,
          tenantIds
        )
      );
    }
  }
}
