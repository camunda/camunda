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
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import org.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import org.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.elasticsearch.common.util.set.Sets;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Component
@Slf4j
public class EntityImportService {

  private final IdentityService identityService;
  private final ReportService reportService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final OptimizeIndexNameService optimizeIndexNameService;

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

  private void prepareVersionListForImportOrFailIfNoneExists(final SingleProcessReportDefinitionExportDto exportDto) {
    final List<String> requiredVersions = new ArrayList<>(exportDto.getData().getProcessDefinitionVersions());
    final List<String> defVersions = getExistingDefinitionVersions(
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getTenantIds()
    );
    exportDto.getData()
      .getProcessDefinitionVersions()
      .removeIf(version -> !defVersions.contains(version));
    if (exportDto.getData().getDefinitionVersions().isEmpty()) {
      throw new OptimizeImportDefinitionDoesNotExistException(
        "Could not find the required definition for this report",
        Sets.newHashSet(DefinitionExceptionItemDto.builder()
                          .type(DefinitionType.PROCESS)
                          .key(exportDto.getData().getProcessDefinitionKey())
                          .tenantIds(exportDto.getData().getTenantIds())
                          .versions(requiredVersions)
                          .build())
      );
    }
  }

  private void validateIndexVersionOrFail(final SingleProcessReportDefinitionExportDto exportDto) {
    if (SingleProcessReportIndex.VERSION != exportDto.getSourceIndexVersion()) {
      throw new OptimizeImportIncorrectIndexVersionException(
        "Could not import because source and target index versions do not match",
        Sets.newHashSet(
          ImportIndexMismatchDto.builder()
            .indexName(optimizeIndexNameService.getOptimizeIndexNameWithVersion(new SingleProcessReportIndex()))
            .sourceIndexVersion(exportDto.getSourceIndexVersion())
            .targetIndexVersion(SingleProcessReportIndex.VERSION)
            .build()
        )
      );
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
      throw new OptimizeImportForbiddenException(
        String.format(
          "User with ID [%s] is not authorized to access the required definition.",
          userId
        ),
        Sets.newHashSet(DefinitionExceptionItemDto.builder()
                          .type(definitionType)
                          .key(definitionKey)
                          .tenantIds(tenantIds)
                          .build())
      );
    }
  }
}
