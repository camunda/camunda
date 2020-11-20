/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import org.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import org.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.elasticsearch.common.util.set.Sets;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Component
@Slf4j
public class ReportImportService {


  private final ReportService reportService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final OptimizeIndexNameService optimizeIndexNameService;

  public IdResponseDto importProcessReportIntoCollection(final String userId,
                                                         final String collectionId,
                                                         final SingleProcessReportDefinitionExportDto exportedDto) {
    validateIndexVersionOrFail(new SingleProcessReportIndex(), exportedDto);
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

  public IdResponseDto importDecisionReportIntoCollection(final String userId,
                                                          final String collectionId,
                                                          final SingleDecisionReportDefinitionExportDto exportedDto) {
    validateIndexVersionOrFail(new SingleDecisionReportIndex(), exportedDto);
    prepareVersionListForImportOrFailIfNoneExists(exportedDto);
    validateAuthorizedToAccessDefinitionOrFail(
      userId,
      DefinitionType.DECISION,
      exportedDto.getData().getDecisionDefinitionKey(),
      exportedDto.getData().getTenantIds()
    );

    return reportService.importReport(
      userId,
      createDecisionReportDefinition(exportedDto),
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

  private SingleDecisionReportDefinitionRequestDto createDecisionReportDefinition(
    final SingleDecisionReportDefinitionExportDto exportedDto) {
    final SingleDecisionReportDefinitionRequestDto reportDefinition =
      new SingleDecisionReportDefinitionRequestDto(exportedDto.getData());
    reportDefinition.setName(exportedDto.getName());
    reportDefinition.setCreated(OffsetDateTime.now());
    return reportDefinition;
  }

  private void prepareVersionListForImportOrFailIfNoneExists(final SingleDecisionReportDefinitionExportDto exportDto) {
    final List<String> existingRequiredVersions = prepareVersionListForImportOrFailIfNoneExists(
      DefinitionType.DECISION,
      exportDto.getData().getDecisionDefinitionKey(),
      exportDto.getData().getDecisionDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
    exportDto.getData()
      .getDecisionDefinitionVersions()
      .removeIf(version -> !existingRequiredVersions.contains(version));
  }

  private void prepareVersionListForImportOrFailIfNoneExists(final SingleProcessReportDefinitionExportDto exportDto) {
    final List<String> existingRequiredVersions = prepareVersionListForImportOrFailIfNoneExists(
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getProcessDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
    exportDto.getData()
      .getProcessDefinitionVersions()
      .removeIf(version -> !existingRequiredVersions.contains(version));
  }

  private List<String> prepareVersionListForImportOrFailIfNoneExists(final DefinitionType definitionType,
                                                                     final String definitionKey,
                                                                     final List<String> definitionVersions,
                                                                     final List<String> tenantIds) {
    final List<String> requiredVersions = new ArrayList<>(definitionVersions);
    final List<String> defVersions = getExistingDefinitionVersions(
      definitionType,
      definitionKey,
      tenantIds
    );
    definitionVersions.removeIf(version -> !defVersions.contains(version));
    if (definitionVersions.isEmpty()) {
      throw new OptimizeImportDefinitionDoesNotExistException(
        "Could not find the required definition for this report",
        Sets.newHashSet(DefinitionExceptionItemDto.builder()
                          .type(definitionType)
                          .key(definitionKey)
                          .tenantIds(tenantIds)
                          .versions(requiredVersions)
                          .build()
        )
      );
    }
    return definitionVersions;
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

  private void validateIndexVersionOrFail(final DefaultIndexMappingCreator targetIndex,
                                          final ReportDefinitionExportDto exportDto) {
    if (targetIndex.getVersion() != exportDto.getSourceIndexVersion()) {
      throw new OptimizeImportIncorrectIndexVersionException(
        "Could not import because source and target index versions do not match",
        Sets.newHashSet(
          ImportIndexMismatchDto.builder()
            .indexName(optimizeIndexNameService.getOptimizeIndexNameWithVersion(targetIndex))
            .sourceIndexVersion(exportDto.getSourceIndexVersion())
            .targetIndexVersion(targetIndex.getVersion())
            .build()
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
