/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.entities.report;

import static io.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_DECISION_REPORT;
import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.SINGLE_PROCESS_REPORT;
import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAllOrLatest;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import io.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex;
import io.camunda.optimize.service.db.schema.index.report.SingleDecisionReportIndex;
import io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import io.camunda.optimize.service.exceptions.OptimizeImportDescriptionNotValidException;
import io.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import io.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import io.camunda.optimize.service.util.DataUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ReportImportService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ReportImportService.class);
  private final ReportService reportService;
  private final ReportWriter reportWriter;
  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final OptimizeIndexNameService optimizeIndexNameService;

  public ReportImportService(
      final ReportService reportService,
      final ReportWriter reportWriter,
      final DefinitionService definitionService,
      final DataSourceDefinitionAuthorizationService definitionAuthorizationService,
      final OptimizeIndexNameService optimizeIndexNameService) {
    this.reportService = reportService;
    this.reportWriter = reportWriter;
    this.definitionService = definitionService;
    this.definitionAuthorizationService = definitionAuthorizationService;
    this.optimizeIndexNameService = optimizeIndexNameService;
  }

  public void importReportsIntoCollection(
      final String collectionId,
      final List<ReportDefinitionExportDto> reportsToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    importReportsIntoCollection(null, collectionId, reportsToImport, originalIdToNewIdMap);
  }

  public void importReportsIntoCollection(
      final String userId,
      final String collectionId,
      final List<ReportDefinitionExportDto> reportsToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    final List<ReportDefinitionExportDto> singleReportsToImport =
        reportsToImport.stream()
            .filter(
                entity ->
                    SINGLE_PROCESS_REPORT.equals(entity.getExportEntityType())
                        || SINGLE_DECISION_REPORT.equals(entity.getExportEntityType()))
            .toList();
    final List<ReportDefinitionExportDto> combinedReportsToImport =
        reportsToImport.stream()
            .filter(entity -> COMBINED_REPORT.equals(entity.getExportEntityType()))
            .toList();

    singleReportsToImport.forEach(
        reportToImport ->
            importReportIntoCollection(userId, collectionId, reportToImport, originalIdToNewIdMap));

    combinedReportsToImport.forEach(
        reportToImport ->
            importReportIntoCollection(userId, collectionId, reportToImport, originalIdToNewIdMap));
  }

  public void validateAllReportsOrFail(
      final CollectionDefinitionDto collection,
      final List<ReportDefinitionExportDto> reportsToImport) {
    validateAllReportsOrFail(null, collection, reportsToImport);
  }

  public void validateAllReportsOrFail(
      final String userId,
      final CollectionDefinitionDto collection,
      final List<ReportDefinitionExportDto> reportsToImport) {
    final Set<ImportIndexMismatchDto> indexMismatches = new HashSet<>();
    final Set<DefinitionExceptionItemDto> missingDefinitions = new HashSet<>();
    final Set<DefinitionExceptionItemDto> forbiddenDefinitions = new HashSet<>();
    final Set<ConflictedItemDto> definitionsNotInScope = new HashSet<>();
    final Set<ConflictedItemDto> tenantsNotInScope = new HashSet<>();
    final Set<String> invalidReportIds = new HashSet<>();

    reportsToImport.forEach(
        reportExportDto -> {
          try {
            validateReportOrFail(userId, collection, reportExportDto);
          } catch (final OptimizeImportIncorrectIndexVersionException e) {
            indexMismatches.addAll(e.getMismatchingIndices());
          } catch (final OptimizeImportDefinitionDoesNotExistException e) {
            missingDefinitions.addAll(e.getMissingDefinitions());
          } catch (final OptimizeImportForbiddenException e) {
            forbiddenDefinitions.addAll(e.getForbiddenDefinitions());
          } catch (final OptimizeNonDefinitionScopeCompliantException e) {
            definitionsNotInScope.addAll(e.getConflictedItems());
          } catch (final OptimizeNonTenantScopeCompliantException e) {
            tenantsNotInScope.addAll(e.getConflictedItems());
          } catch (final OptimizeImportDescriptionNotValidException e) {
            invalidReportIds.addAll(e.getInvalidEntityIds());
          }
        });

    if (!indexMismatches.isEmpty()) {
      throw new OptimizeImportIncorrectIndexVersionException(
          "Could not import because source and target index versions do not match for at least one report.",
          indexMismatches);
    }

    if (!missingDefinitions.isEmpty()) {
      throw new OptimizeImportDefinitionDoesNotExistException(
          "Could not import because at least one required definition does not exist.",
          missingDefinitions);
    }

    if (!forbiddenDefinitions.isEmpty()) {
      throw new OptimizeImportForbiddenException(
          String.format(
              "Could not import because user with ID [%s] is not authorized to access at least one of the required "
                  + "definitions.",
              userId),
          forbiddenDefinitions);
    }

    if (!definitionsNotInScope.isEmpty()) {
      throw new OptimizeNonDefinitionScopeCompliantException(definitionsNotInScope);
    }

    if (!tenantsNotInScope.isEmpty()) {
      throw new OptimizeNonTenantScopeCompliantException(tenantsNotInScope);
    }

    if (!invalidReportIds.isEmpty()) {
      throw new OptimizeImportDescriptionNotValidException(invalidReportIds);
    }
  }

  private void importReportIntoCollection(
      final String userId,
      final String collectionId,
      final ReportDefinitionExportDto reportToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    switch (reportToImport.getExportEntityType()) {
      case SINGLE_PROCESS_REPORT ->
          importProcessReportIntoCollection(
              Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
              collectionId,
              (SingleProcessReportDefinitionExportDto) reportToImport,
              originalIdToNewIdMap);
      case SINGLE_DECISION_REPORT ->
          importDecisionReportIntoCollection(
              Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
              collectionId,
              (SingleDecisionReportDefinitionExportDto) reportToImport,
              originalIdToNewIdMap);
      case COMBINED_REPORT ->
          importCombinedProcessReportIntoCollection(
              Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
              collectionId,
              (CombinedProcessReportDefinitionExportDto) reportToImport,
              originalIdToNewIdMap);
      default ->
          throw new OptimizeRuntimeException(
              "Unknown single report entity type: " + reportToImport.getExportEntityType());
    }
  }

  private void importProcessReportIntoCollection(
      final String userId,
      final String collectionId,
      final SingleProcessReportDefinitionExportDto reportToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    final IdResponseDto newId =
        importReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            reportToImport,
            collectionId);
    originalIdToNewIdMap.put(
        reportToImport.getId(), new EntityIdResponseDto(newId.getId(), EntityType.REPORT));
  }

  private void importCombinedProcessReportIntoCollection(
      final String userId,
      final String collectionId,
      final CombinedProcessReportDefinitionExportDto reportToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    prepareCombinedReportForImport(reportToImport, originalIdToNewIdMap);
    final IdResponseDto newId =
        importReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            reportToImport,
            collectionId);
    originalIdToNewIdMap.put(
        reportToImport.getId(), new EntityIdResponseDto(newId.getId(), EntityType.REPORT));
  }

  private void importDecisionReportIntoCollection(
      final String userId,
      final String collectionId,
      final SingleDecisionReportDefinitionExportDto reportToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    final IdResponseDto newId =
        importReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            reportToImport,
            collectionId);
    originalIdToNewIdMap.put(
        reportToImport.getId(), new EntityIdResponseDto(newId.getId(), EntityType.REPORT));
  }

  public IdResponseDto importReport(
      final String userId,
      final ReportDefinitionExportDto reportToImport,
      final String newCollectionId) {
    switch (reportToImport.getExportEntityType()) {
      case COMBINED_REPORT:
        return reportWriter.createNewCombinedReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            ((CombinedProcessReportDefinitionExportDto) reportToImport).getData(),
            reportToImport.getName(),
            reportToImport.getDescription(),
            newCollectionId);
      case SINGLE_PROCESS_REPORT:
        return reportWriter.createNewSingleProcessReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            ((SingleProcessReportDefinitionExportDto) reportToImport).getData(),
            reportToImport.getName(),
            reportToImport.getDescription(),
            newCollectionId);
      case SINGLE_DECISION_REPORT:
        return reportWriter.createNewSingleDecisionReport(
            Optional.ofNullable(userId).orElse(API_IMPORT_OWNER_NAME),
            ((SingleDecisionReportDefinitionExportDto) reportToImport).getData(),
            reportToImport.getName(),
            reportToImport.getDescription(),
            newCollectionId);
      default:
        throw new IllegalStateException(
            "Unsupported entity type: " + reportToImport.getExportEntityType());
    }
  }

  private void prepareCombinedReportForImport(
      final CombinedProcessReportDefinitionExportDto reportToImport,
      final Map<String, EntityIdResponseDto> originalIdToNewIdMap) {
    // Map single report items within combined report to new IDs
    final List<CombinedReportItemDto> newSingleReportItems =
        reportToImport.getData().getReports().stream()
            .map(
                reportItem ->
                    new CombinedReportItemDto(
                        originalIdToNewIdMap.get(reportItem.getId()).getId(),
                        reportItem.getColor()))
            .toList();
    reportToImport.getData().setReports(newSingleReportItems);
  }

  private void removeMissingVersionsOrFailIfNoVersionsExist(
      final SingleDecisionReportDefinitionExportDto exportDto) {
    removeMissingVersionsOrFailIfNoneExist(
        DefinitionType.DECISION,
        exportDto.getData().getDecisionDefinitionKey(),
        exportDto.getData().getDecisionDefinitionVersions(),
        exportDto.getData().getTenantIds());
  }

  private void removeMissingVersionsOrFailIfNoVersionsExist(
      final SingleProcessReportDefinitionExportDto exportDto) {
    removeMissingVersionsOrFailIfNoneExist(
        DefinitionType.PROCESS,
        exportDto.getData().getProcessDefinitionKey(),
        exportDto.getData().getProcessDefinitionVersions(),
        exportDto.getData().getTenantIds());
  }

  private void removeMissingVersionsOrFailIfNoneExist(
      final DefinitionType definitionType,
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds) {
    final List<String> requiredVersions = new ArrayList<>(definitionVersions);
    final boolean isAllOrLatest = isDefinitionVersionSetToAllOrLatest(requiredVersions);
    final List<String> existingVersions =
        getExistingDefinitionVersions(definitionType, definitionKey, tenantIds);
    if (!isAllOrLatest) {
      // if specific versions are required, remove all nonexistent versions
      definitionVersions.removeIf(version -> !existingVersions.contains(version));
    }
    if (isAllOrLatest && existingVersions.isEmpty()
        || !isAllOrLatest && definitionVersions.isEmpty()) {
      throw new OptimizeImportDefinitionDoesNotExistException(
          "Could not find the required definition for this report",
          DataUtil.newHashSet(
              DefinitionExceptionItemDto.builder()
                  .type(definitionType)
                  .key(definitionKey)
                  .tenantIds(tenantIds)
                  .versions(requiredVersions)
                  .build()));
    }
  }

  private List<String> getExistingDefinitionVersions(
      final DefinitionType definitionType,
      final String definitionKey,
      final List<String> tenantIds) {
    return definitionService
        .getDefinitionVersions(definitionType, definitionKey, tenantIds)
        .stream()
        .map(DefinitionVersionResponseDto::getVersion)
        .toList();
  }

  private void populateDefinitionXml(final SingleProcessReportDefinitionExportDto exportDto) {
    final Optional<ProcessDefinitionOptimizeDto> definitionWithXml =
        definitionService.getDefinitionWithXmlAsService(
            DefinitionType.PROCESS,
            exportDto.getData().getProcessDefinitionKey(),
            exportDto.getData().getDefinitionVersions(),
            exportDto.getData().getTenantIds());
    definitionWithXml.ifPresent(
        def -> exportDto.getData().getConfiguration().setXml(def.getBpmn20Xml()));
  }

  private void populateDefinitionXml(final SingleDecisionReportDefinitionExportDto exportDto) {
    final Optional<DecisionDefinitionOptimizeDto> definitionWithXml =
        definitionService.getDefinitionWithXmlAsService(
            DefinitionType.DECISION,
            exportDto.getData().getDecisionDefinitionKey(),
            exportDto.getData().getDefinitionVersions(),
            exportDto.getData().getTenantIds());
    definitionWithXml.ifPresent(
        def -> exportDto.getData().getConfiguration().setXml(def.getDmn10Xml()));
  }

  private void validateReportOrFail(
      final String userId,
      final CollectionDefinitionDto collection,
      final ReportDefinitionExportDto reportToImport) {
    try {
      reportService.validateReportDescription(reportToImport.getDescription());
    } catch (final OptimizeValidationException ex) {
      throw new OptimizeImportDescriptionNotValidException(Set.of(reportToImport.getId()));
    }
    switch (reportToImport.getExportEntityType()) {
      case SINGLE_PROCESS_REPORT:
        final SingleProcessReportDefinitionExportDto processExport =
            (SingleProcessReportDefinitionExportDto) reportToImport;

        validateIndexVersionOrFail(
            SingleProcessReportIndex.VERSION, SINGLE_PROCESS_REPORT_INDEX_NAME, processExport);
        removeMissingVersionsOrFailIfNoVersionsExist(processExport);
        if (userId != null) {
          validateAuthorizedToAccessDefinitionOrFail(userId, processExport);
        }
        validateCollectionScopeOrFail(collection, processExport);
        populateDefinitionXml(processExport);
        break;
      case SINGLE_DECISION_REPORT:
        final SingleDecisionReportDefinitionExportDto decisionExport =
            (SingleDecisionReportDefinitionExportDto) reportToImport;
        validateIndexVersionOrFail(
            SingleDecisionReportIndex.VERSION, SINGLE_DECISION_REPORT_INDEX_NAME, decisionExport);
        removeMissingVersionsOrFailIfNoVersionsExist(decisionExport);
        if (userId != null) {
          validateAuthorizedToAccessDefinitionOrFail(userId, decisionExport);
        }
        validateCollectionScopeOrFail(collection, decisionExport);
        populateDefinitionXml(decisionExport);
        break;
      case COMBINED_REPORT:
        final CombinedProcessReportDefinitionExportDto combinedExport =
            (CombinedProcessReportDefinitionExportDto) reportToImport;
        validateIndexVersionOrFail(
            CombinedReportIndex.VERSION, COMBINED_REPORT_INDEX_NAME, combinedExport);
        break;
      default:
        throw new OptimizeRuntimeException(
            "Unknown report entity type: " + reportToImport.getExportEntityType());
    }
  }

  private String getFullyQualifiedIndexName(final String name, final int version) {
    final String indexName =
        OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
            name, Integer.toString(version));
    return optimizeIndexNameService.getOptimizeIndexAliasForIndexNameAndPrefix(
        indexName, optimizeIndexNameService.getIndexPrefix());
  }

  private void validateIndexVersionOrFail(
      final int targetVersion,
      final String rawIndexName,
      final ReportDefinitionExportDto exportDto) {
    final String targetName = getFullyQualifiedIndexName(rawIndexName, targetVersion);
    if (targetVersion != exportDto.getSourceIndexVersion()) {
      throw new OptimizeImportIncorrectIndexVersionException(
          "Could not import because source and target index versions do not match",
          DataUtil.newHashSet(
              ImportIndexMismatchDto.builder()
                  .indexName(targetName)
                  .sourceIndexVersion(exportDto.getSourceIndexVersion())
                  .targetIndexVersion(targetVersion)
                  .build()));
    }
  }

  private void validateAuthorizedToAccessDefinitionOrFail(
      final String userId, final SingleProcessReportDefinitionExportDto exportDto) {
    validateAuthorizedToAccessDefinitionOrFail(
        userId,
        DefinitionType.PROCESS,
        exportDto.getData().getProcessDefinitionKey(),
        exportDto.getData().getTenantIds());
  }

  private void validateAuthorizedToAccessDefinitionOrFail(
      final String userId, final SingleDecisionReportDefinitionExportDto exportDto) {
    validateAuthorizedToAccessDefinitionOrFail(
        userId,
        DefinitionType.DECISION,
        exportDto.getData().getDecisionDefinitionKey(),
        exportDto.getData().getTenantIds());
  }

  private void validateAuthorizedToAccessDefinitionOrFail(
      final String userId,
      final DefinitionType definitionType,
      final String definitionKey,
      final List<String> tenantIds) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, definitionType, definitionKey, tenantIds)) {
      throw new OptimizeImportForbiddenException(
          String.format(
              "User with ID [%s] is not authorized to access the required definition.", userId),
          DataUtil.newHashSet(
              DefinitionExceptionItemDto.builder()
                  .type(definitionType)
                  .key(definitionKey)
                  .tenantIds(tenantIds)
                  .build()));
    }
  }

  private void validateCollectionScopeOrFail(
      final CollectionDefinitionDto collection,
      final SingleProcessReportDefinitionExportDto reportToImport) {
    if (collection != null) {
      reportService.ensureCompliesWithCollectionScope(
          reportToImport.getData().getDefinitions(), DefinitionType.PROCESS, collection);
    }
  }

  private void validateCollectionScopeOrFail(
      final CollectionDefinitionDto collection,
      final SingleDecisionReportDefinitionExportDto reportToImport) {
    if (collection != null) {
      reportService.ensureCompliesWithCollectionScope(
          reportToImport.getData().getDefinitions(), DefinitionType.DECISION, collection);
    }
  }
}
