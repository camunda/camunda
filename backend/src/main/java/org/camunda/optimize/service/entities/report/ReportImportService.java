/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import org.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import org.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.util.set.Sets;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;

@AllArgsConstructor
@Component
@Slf4j
public class ReportImportService {

  private final ReportService reportService;
  private final ReportWriter reportWriter;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final AuthorizedCollectionService collectionService;
  private final OptimizeIndexNameService optimizeIndexNameService;

  public Map<String, IdResponseDto> importReportsIntoCollection(final String userId,
                                                                final String collectionId,
                                                                final List<OptimizeEntityExportDto> exportedDtos) {
    final Map<String, IdResponseDto> originalIdToNewIdMap = new HashMap<>();
    final List<OptimizeEntityExportDto> singleReportsToImport = exportedDtos.stream()
      .filter(entity -> SINGLE_PROCESS_REPORT.equals(entity.getExportEntityType())
        || SINGLE_DECISION_REPORT.equals(entity.getExportEntityType()))
      .collect(toList());
    final List<OptimizeEntityExportDto> combinedReportsToImport = exportedDtos.stream()
      .filter(entity -> COMBINED.equals(entity.getExportEntityType()))
      .collect(toList());

    singleReportsToImport.forEach(
      reportExportDto -> importReportIntoCollection(userId, collectionId, reportExportDto, originalIdToNewIdMap)
    );

    combinedReportsToImport.forEach(
      reportExportDto -> importReportIntoCollection(userId, collectionId, reportExportDto, originalIdToNewIdMap)
    );

    return originalIdToNewIdMap;
  }

  public void validateAllReportsOrFail(final String userId,
                                       final String collectionId,
                                       final List<OptimizeEntityExportDto> exportedDtos) {
    final Set<ImportIndexMismatchDto> indexMismatches = new HashSet<>();
    final Set<DefinitionExceptionItemDto> missingDefinitions = new HashSet<>();
    final Set<DefinitionExceptionItemDto> forbiddenDefinitions = new HashSet<>();
    final Set<ConflictedItemDto> definitionsNotInScope = new HashSet<>();
    final Set<ConflictedItemDto> tenantsNotInScope = new HashSet<>();

    exportedDtos.forEach(
      reportExportDto -> {
        try {
          validateReportOrFail(userId, collectionId, reportExportDto);
        } catch (OptimizeImportIncorrectIndexVersionException e) {
          indexMismatches.addAll(e.getMismatchingIndices());
        } catch (OptimizeImportDefinitionDoesNotExistException e) {
          missingDefinitions.addAll(e.getMissingDefinitions());
        } catch (OptimizeImportForbiddenException e) {
          forbiddenDefinitions.addAll(e.getForbiddenDefinitions());
        } catch (OptimizeNonDefinitionScopeCompliantException e) {
          definitionsNotInScope.addAll(e.getConflictedItems());
        } catch (OptimizeNonTenantScopeCompliantException e) {
          tenantsNotInScope.addAll(e.getConflictedItems());
        }
      }
    );

    if (!indexMismatches.isEmpty()) {
      throw new OptimizeImportIncorrectIndexVersionException(
        "Could not import because source and target index versions do not match for at least one entity.",
        indexMismatches
      );
    }

    if (!missingDefinitions.isEmpty()) {
      throw new OptimizeImportDefinitionDoesNotExistException(
        "Could not import because at least one required definition does not exist.",
        missingDefinitions
      );
    }

    if (!forbiddenDefinitions.isEmpty()) {
      throw new OptimizeImportForbiddenException(
        String.format(
          "Could not import because user with ID [%s] is not authorized to access at least one of the required " +
            "definitions.",
          userId
        ),
        forbiddenDefinitions
      );
    }

    if (!definitionsNotInScope.isEmpty()) {
      throw new OptimizeNonDefinitionScopeCompliantException(definitionsNotInScope);
    }

    if (!tenantsNotInScope.isEmpty()) {
      throw new OptimizeNonTenantScopeCompliantException(tenantsNotInScope);
    }
  }

  private void validateReportOrFail(final String userId,
                                    final String collectionId,
                                    final OptimizeEntityExportDto exportedDto) {
    final CollectionDefinitionDto collection = Optional.ofNullable(collectionId)
      .map(collId -> collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collId).getDefinitionDto())
      .orElse(null);

    switch (exportedDto.getExportEntityType()) {
      case SINGLE_PROCESS_REPORT:
        final SingleProcessReportDefinitionExportDto processExport =
          (SingleProcessReportDefinitionExportDto) exportedDto;
        validateIndexVersionOrFail(new SingleProcessReportIndex(), processExport);
        removeMissingVersionsOrFailIfNoVersionsExist(processExport);
        validateAuthorizedToAccessDefinitionOrFail(userId, processExport);
        validateCollectionScopeOrFail(collection, processExport);
        populateDefinitionXml(processExport);
        break;
      case SINGLE_DECISION_REPORT:
        final SingleDecisionReportDefinitionExportDto decisionExport =
          (SingleDecisionReportDefinitionExportDto) exportedDto;
        validateIndexVersionOrFail(new SingleDecisionReportIndex(), decisionExport);
        removeMissingVersionsOrFailIfNoVersionsExist(decisionExport);
        validateAuthorizedToAccessDefinitionOrFail(userId, decisionExport);
        validateCollectionScopeOrFail(collection, decisionExport);
        populateDefinitionXml(decisionExport);
        break;
      case COMBINED:
        final CombinedProcessReportDefinitionExportDto combinedExport =
          (CombinedProcessReportDefinitionExportDto) exportedDto;
        validateIndexVersionOrFail(new CombinedReportIndex(), combinedExport);
        break;
      default:
        throw new OptimizeRuntimeException("Unknown report entity type: " + exportedDto.getExportEntityType());
    }
  }

  private void importReportIntoCollection(final String userId,
                                          final String collectionId,
                                          final OptimizeEntityExportDto exportedDto,
                                          final Map<String, IdResponseDto> originalIdToNewIdMap) {
    switch (exportedDto.getExportEntityType()) {
      case SINGLE_PROCESS_REPORT:
        importProcessReportIntoCollection(
          userId,
          collectionId,
          (SingleProcessReportDefinitionExportDto) exportedDto,
          originalIdToNewIdMap
        );
        break;
      case SINGLE_DECISION_REPORT:
        importDecisionReportIntoCollection(
          userId,
          collectionId,
          (SingleDecisionReportDefinitionExportDto) exportedDto,
          originalIdToNewIdMap
        );
        break;
      case COMBINED:
        importCombinedProcessReportIntoCollection(
          userId,
          collectionId,
          (CombinedProcessReportDefinitionExportDto) exportedDto,
          originalIdToNewIdMap
        );
        break;
      default:
        throw new OptimizeRuntimeException("Unknown single report entity type: " + exportedDto.getExportEntityType());
    }
  }

  private void importProcessReportIntoCollection(final String userId,
                                                 final String collectionId,
                                                 final SingleProcessReportDefinitionExportDto exportedDto,
                                                 final Map<String, IdResponseDto> originalIdToNewIdMap) {
    final IdResponseDto newId = importReport(
      userId,
      createProcessReportDefinition(exportedDto),
      collectionId
    );
    originalIdToNewIdMap.put(exportedDto.getId(), newId);
  }

  private void importCombinedProcessReportIntoCollection(final String userId,
                                                         final String collectionId,
                                                         final CombinedProcessReportDefinitionExportDto exportedDto,
                                                         final Map<String, IdResponseDto> originalIdToNewIdMap) {
    final IdResponseDto newId = importReport(
      userId,
      createCombinedReportDefinition(exportedDto, originalIdToNewIdMap),
      collectionId
    );
    originalIdToNewIdMap.put(exportedDto.getId(), newId);
  }

  private void importDecisionReportIntoCollection(final String userId,
                                                  final String collectionId,
                                                  final SingleDecisionReportDefinitionExportDto exportedDto,
                                                  final Map<String, IdResponseDto> originalIdToNewIdMap) {
    final IdResponseDto newId = importReport(
      userId,
      createDecisionReportDefinition(exportedDto),
      collectionId
    );
    originalIdToNewIdMap.put(exportedDto.getId(), newId);
  }

  public IdResponseDto importReport(final String userId,
                                    final ReportDefinitionDto<?> reportDefinitionDto,
                                    final String newCollectionId) {
    reportDefinitionDto.setCollectionId(newCollectionId);
    switch (reportDefinitionDto.getReportType()) {
      case PROCESS:
        if (reportDefinitionDto.isCombined()) {
          return reportWriter.createNewCombinedReport(
            userId,
            (CombinedReportDataDto) reportDefinitionDto.getData(),
            reportDefinitionDto.getName(),
            newCollectionId
          );
        }
        SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
          (SingleProcessReportDefinitionRequestDto) reportDefinitionDto;
        return reportWriter.createNewSingleProcessReport(
          userId, singleProcessReportDefinitionDto.getData(), reportDefinitionDto.getName(), newCollectionId
        );
      case DECISION:
        SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
          (SingleDecisionReportDefinitionRequestDto) reportDefinitionDto;
        return reportWriter.createNewSingleDecisionReport(
          userId, singleDecisionReportDefinitionDto.getData(), reportDefinitionDto.getName(), newCollectionId
        );
      default:
        throw new IllegalStateException("Unsupported reportType: " + reportDefinitionDto.getReportType());
    }
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

  private CombinedReportDefinitionRequestDto createCombinedReportDefinition(
    final CombinedProcessReportDefinitionExportDto exportedDto,
    final Map<String, IdResponseDto> originalIdToNewIdMap) {
    final CombinedReportDefinitionRequestDto reportDefinition =
      new CombinedReportDefinitionRequestDto(exportedDto.getData());

    // Map single report items within combined report to new IDs
    final List<CombinedReportItemDto> newSingleReportItems = exportedDto.getData()
      .getReports()
      .stream()
      .map(reportItem -> new CombinedReportItemDto(
        originalIdToNewIdMap.get(reportItem.getId()).getId(),
        reportItem.getColor()
      ))
      .collect(toList());
    reportDefinition.getData().setReports(newSingleReportItems);
    reportDefinition.setName(exportedDto.getName());
    reportDefinition.setCreated(OffsetDateTime.now());

    return reportDefinition;
  }

  private void removeMissingVersionsOrFailIfNoVersionsExist(final SingleDecisionReportDefinitionExportDto exportDto) {
    removeMissingVersionsOrFailIfNoneExist(
      DefinitionType.DECISION,
      exportDto.getData().getDecisionDefinitionKey(),
      exportDto.getData().getDecisionDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
  }

  private void removeMissingVersionsOrFailIfNoVersionsExist(final SingleProcessReportDefinitionExportDto exportDto) {
    removeMissingVersionsOrFailIfNoneExist(
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getProcessDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
  }

  private void removeMissingVersionsOrFailIfNoneExist(final DefinitionType definitionType,
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

  private void populateDefinitionXml(final SingleProcessReportDefinitionExportDto exportDto) {
    final Optional<ProcessDefinitionOptimizeDto> definitionWithXml = definitionService.getDefinitionWithXmlAsService(
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
    definitionWithXml.ifPresent(def -> exportDto.getData().getConfiguration().setXml(def.getBpmn20Xml()));
  }

  private void populateDefinitionXml(final SingleDecisionReportDefinitionExportDto exportDto) {
    final Optional<DecisionDefinitionOptimizeDto> definitionWithXml = definitionService.getDefinitionWithXmlAsService(
      DefinitionType.DECISION,
      exportDto.getData().getDecisionDefinitionKey(),
      exportDto.getData().getDefinitionVersions(),
      exportDto.getData().getTenantIds()
    );
    definitionWithXml.ifPresent(def -> exportDto.getData().getConfiguration().setXml(def.getDmn10Xml()));
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
                                                          final SingleProcessReportDefinitionExportDto exportDto) {
    validateAuthorizedToAccessDefinitionOrFail(
      userId,
      DefinitionType.PROCESS,
      exportDto.getData().getProcessDefinitionKey(),
      exportDto.getData().getTenantIds()
    );
  }

  private void validateAuthorizedToAccessDefinitionOrFail(final String userId,
                                                          final SingleDecisionReportDefinitionExportDto exportDto) {
    validateAuthorizedToAccessDefinitionOrFail(
      userId,
      DefinitionType.DECISION,
      exportDto.getData().getDecisionDefinitionKey(),
      exportDto.getData().getTenantIds()
    );
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

  private void validateCollectionScopeOrFail(@Nullable final CollectionDefinitionDto collection,
                                             final SingleProcessReportDefinitionExportDto exportedDto) {
    if (collection != null) {
      reportService.ensureCompliesWithCollectionScope(
        exportedDto.getData().getProcessDefinitionKey(),
        exportedDto.getData().getTenantIds(),
        DefinitionType.PROCESS,
        collection
      );
    }
  }

  private void validateCollectionScopeOrFail(@Nullable final CollectionDefinitionDto collection,
                                             final SingleDecisionReportDefinitionExportDto exportedDto) {
    if (collection != null) {
      reportService.ensureCompliesWithCollectionScope(
        exportedDto.getData().getDecisionDefinitionKey(),
        exportedDto.getData().getTenantIds(),
        DefinitionType.DECISION,
        collection
      );
    }
  }
}
