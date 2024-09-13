/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.report;

import static io.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.COMPLIANT;
import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_DEFINITION_COMPLIANT;
import static io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_TENANT_COMPLIANT;
import static io.camunda.optimize.service.util.BpmnModelUtil.extractProcessDefinitionName;
import static io.camunda.optimize.service.util.DmnModelUtil.extractDecisionDefinitionName;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeReportConflictException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.relations.CollectionReferencingService;
import io.camunda.optimize.service.relations.ReportRelationService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.ReportAuthorizationService;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import io.camunda.optimize.service.util.ValidationHelper;
import io.camunda.optimize.util.SuppressionConstants;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportService implements CollectionReferencingService {

  private static final String DEFAULT_REPORT_NAME = "New Report";

  private final ReportWriter reportWriter;
  private final ReportReader reportReader;
  private final ReportAuthorizationService reportAuthorizationService;
  private final ReportRelationService reportRelationService;
  private final AuthorizedCollectionService collectionService;
  private final AbstractIdentityService identityService;
  private final DefinitionService defintionService;

  private static void copyDefinitionMetaDataToUpdate(
      final ReportDefinitionDto from, final ReportDefinitionUpdateDto to, final String userId) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setDescription(from.getDescription());
    to.setLastModifier(userId);
    to.setLastModified(from.getLastModified());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(
      final CollectionDefinitionDto definition) {
    return reportReader.getReportsForCollectionOmitXml(definition.getId()).stream()
        .map(
            reportDefinitionDto ->
                new ConflictedItemDto(
                    reportDefinitionDto.getId(),
                    ConflictedItemType.COLLECTION,
                    reportDefinitionDto.getName()))
        .collect(toSet());
  }

  @Override
  public void handleCollectionDeleted(final CollectionDefinitionDto definition) {
    final List<ReportDefinitionDto> reportsToDelete = getReportsForCollection(definition.getId());
    for (final ReportDefinitionDto reportDefinition : reportsToDelete) {
      reportRelationService.handleDeleted(reportDefinition);
    }
    reportWriter.deleteAllReportsOfCollection(definition.getId());
  }

  public IdResponseDto createNewSingleDecisionReport(
      final String userId, final DecisionReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    validateReportDescription(definitionDto.getDescription());
    return createReport(
        userId,
        definitionDto,
        DecisionReportDataDto::new,
        reportWriter::createNewSingleDecisionReport);
  }

  public IdResponseDto createNewSingleProcessReport(
      final String userId, final ProcessReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    validateReportDescription(definitionDto.getDescription());
    Optional.ofNullable(definitionDto.getData())
        .ifPresent(
            data -> {
              ValidationHelper.validateProcessFilters(data.getFilter());
              Optional.ofNullable(data.getConfiguration())
                  .ifPresent(
                      config ->
                          ValidationHelper.validateAggregationTypes(config.getAggregationTypes()));
            });
    return createReport(
        userId,
        definitionDto,
        ProcessReportDataDto::new,
        reportWriter::createNewSingleProcessReport);
  }

  public ConflictResponseDto getReportDeleteConflictingItems(
      final String userId, final String reportId) {
    final ReportDefinitionDto currentReportVersion =
        getReportDefinition(reportId, userId).getDefinitionDto();
    return new ConflictResponseDto(getConflictedItemsForDeleteReport(currentReportVersion));
  }

  public IdResponseDto copyReport(
      final String reportId, final String userId, final String newReportName) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition =
        getReportDefinition(reportId, userId);
    final ReportDefinitionDto oldReportDefinition = authorizedReportDefinition.getDefinitionDto();

    return copyAndMoveReport(
        reportId, userId, oldReportDefinition.getCollectionId(), newReportName);
  }

  public List<IdResponseDto> getAllReportIdsInCollection(final String collectionId) {
    return reportReader.getReportsForCollectionOmitXml(collectionId).stream()
        .map(report -> new IdResponseDto(report.getId()))
        .collect(toList());
  }

  public List<ReportDefinitionDto> getAllAuthorizedReportsForIds(
      final String userId, final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds).stream()
        .filter(
            reportDefinitionDto ->
                reportAuthorizationService.isAuthorizedToReport(userId, reportDefinitionDto))
        .collect(toList());
  }

  public List<ReportDefinitionDto> getAllReportsForIds(final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds);
  }

  public IdResponseDto copyAndMoveReport(
      @NonNull final String reportId,
      @NonNull final String userId,
      final String collectionId,
      final String newReportName) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition =
        getReportDefinition(reportId, userId);
    final ReportDefinitionDto originalReportDefinition =
        authorizedReportDefinition.getDefinitionDto();
    if (isManagementOrInstantPreviewReport(originalReportDefinition)) {
      throw new OptimizeValidationException(
          "Management and Instant Preview Reports cannot be copied");
    }
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    final String oldCollectionId = originalReportDefinition.getCollectionId();
    final String newCollectionId =
        Objects.equals(oldCollectionId, collectionId) ? oldCollectionId : collectionId;

    final String newName =
        newReportName != null ? newReportName : originalReportDefinition.getName() + " – Copy";

    return copyAndMoveReport(originalReportDefinition, userId, newName, newCollectionId);
  }

  public ReportDefinitionDto<ReportDataDto> getReportDefinition(final String reportId) {
    return reportReader
        .getReport(reportId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Was not able to retrieve report with id ["
                        + reportId
                        + "]"
                        + "from Elasticsearch. Report does not exist."));
  }

  public AuthorizedReportDefinitionResponseDto getReportDefinition(
      final String reportId, final String userId) {
    final ReportDefinitionDto report =
        reportReader
            .getReport(reportId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Was not able to retrieve report with id ["
                            + reportId
                            + "]"
                            + "from Elasticsearch. Report does not exist."));

    final RoleType currentUserRole =
        reportAuthorizationService
            .getAuthorizedRole(userId, report)
            .orElseThrow(
                () ->
                    new ForbiddenException(
                        String.format(
                            "User [%s] is not authorized to access report [%s].",
                            userId, reportId)));
    return new AuthorizedReportDefinitionResponseDto(report, currentUserRole);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterPrivateReports(
      final String userId) {
    final List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();
    return filterAuthorizedReports(userId, reports).stream()
        .sorted(
            Comparator.comparing(
                    o ->
                        ((AuthorizedReportDefinitionResponseDto) o)
                            .getDefinitionDto()
                            .getLastModified())
                .reversed())
        .collect(toList());
  }

  public void deleteAllReportsForProcessDefinitionKey(final String processDefinitionKey) {
    final List<ReportDefinitionDto> reportsForDefinitionKey =
        getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
    reportsForDefinitionKey.forEach(
        report -> removeReportAndAssociatedResources(report.getId(), report));
  }

  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(
      final String processDefinitionKey) {
    return reportReader.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
  }

  public List<ProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(
      final List<String> reportIds) {
    return reportReader.getAllSingleProcessReportsForIdsOmitXml(reportIds);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(
      final String userId, final String collectionId) {
    // verify user is authorized to access collection
    collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    final List<ReportDefinitionDto> reportsInCollection =
        reportReader.getReportsForCollectionOmitXml(collectionId);
    return filterAuthorizedReports(userId, reportsInCollection);
  }

  private List<ReportDefinitionDto> getReportsForCollection(final String collectionId) {
    return reportReader.getReportsForCollectionOmitXml(collectionId);
  }

  public void updateSingleProcessReport(
      final String reportId,
      final ProcessReportDefinitionRequestDto updatedReport,
      final String userId,
      final boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    ValidationHelper.validateProcessFilters(updatedReport.getData().getFilter());
    validateReportDescription(updatedReport.getDescription());
    Optional.ofNullable(updatedReport.getData().getConfiguration())
        .ifPresent(
            config -> ValidationHelper.validateAggregationTypes(config.getAggregationTypes()));

    final ProcessReportDefinitionRequestDto currentReportVersion =
        getSingleProcessReportDefinition(reportId, userId);
    if (isManagementOrInstantPreviewReport(currentReportVersion)) {
      throw new OptimizeValidationException(
          "Management and Instant Preview Reports cannot be updated");
    }
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(
        userId, currentReportVersion.getCollectionId(), updatedReport);
    validateEntityEditorAuthorization(currentReportVersion.getCollectionId());

    final SingleProcessReportDefinitionUpdateDto reportUpdate =
        convertToSingleProcessReportUpdate(updatedReport, userId);

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    reportWriter.updateSingleProcessReport(reportUpdate);
  }

  public void updateDefinitionXmlOfProcessReports(
      final String definitionKey, final String definitionXml) {
    reportWriter.updateProcessDefinitionXmlForProcessReportsWithKey(definitionKey, definitionXml);
  }

  public void updateSingleDecisionReport(
      final String reportId,
      final DecisionReportDefinitionRequestDto updatedReport,
      final String userId,
      final boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    validateReportDescription(updatedReport.getDescription());
    final DecisionReportDefinitionRequestDto currentReportVersion =
        getSingleDecisionReportDefinition(reportId, userId);
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(
        userId, currentReportVersion.getCollectionId(), updatedReport);
    validateEntityEditorAuthorization(currentReportVersion.getCollectionId());

    final SingleDecisionReportDefinitionUpdateDto reportUpdate =
        convertToSingleDecisionReportUpdate(updatedReport, userId);

    if (!force) {
      checkForUpdateConflictsOnSingleDecisionDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    reportWriter.updateSingleDecisionReport(reportUpdate);
  }

  public void deleteReport(final String reportId) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    if (isManagementOrInstantPreviewReport(reportDefinition)) {
      throw new OptimizeValidationException(
          "Management and Instant Preview Reports cannot be deleted manually");
    }
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  public void deleteManagementOrInstantPreviewReport(final String reportId) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  public void deleteReportAsUser(final String userId, final String reportId, final boolean force) {
    final ReportDefinitionDto<?> reportDefinition = getReportOrFail(reportId);
    if (isManagementOrInstantPreviewReport(reportDefinition)) {
      throw new OptimizeValidationException(
          "Management and Instant Preview Reports cannot be deleted");
    }
    getReportWithEditAuthorization(userId, reportDefinition);
    validateEntityEditorAuthorization(reportDefinition.getCollectionId());

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems =
          getConflictedItemsForDeleteReport(reportDefinition);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeReportConflictException(conflictedItems);
      }
    }
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private <T extends ReportDataDto> ReportDefinitionDto<T> getReportOrFail(final String reportId) {
    return reportReader
        .getReport(reportId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Was not able to retrieve report with id ["
                        + reportId
                        + "] from database. Report does not exist."));
  }

  private void removeReportAndAssociatedResources(
      final String reportId, final ReportDefinitionDto reportDefinition) {
    reportRelationService.handleDeleted(reportDefinition);
    reportWriter.deleteSingleReport(reportId);
  }

  private <T extends ReportDefinitionDto<RD>, RD extends ReportDataDto> IdResponseDto createReport(
      final String userId,
      final T reportDefinition,
      final Supplier<RD> defaultDataProvider,
      final CreateReportMethod<RD> createReportMethod) {

    final Optional<T> optionalProvidedDefinition = Optional.ofNullable(reportDefinition);
    final String collectionId =
        optionalProvidedDefinition.map(ReportDefinitionDto::getCollectionId).orElse(null);
    validateEntityEditorAuthorization(collectionId);
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    return createReportMethod.create(
        userId,
        optionalProvidedDefinition
            .map(ReportDefinitionDto::getData)
            .orElse(defaultDataProvider.get()),
        optionalProvidedDefinition.map(ReportDefinitionDto::getName).orElse(DEFAULT_REPORT_NAME),
        optionalProvidedDefinition.map(ReportDefinitionDto::getDescription).orElse(null),
        collectionId);
  }

  private AuthorizedReportDefinitionResponseDto getReportWithEditAuthorization(
      final String userId, final ReportDefinitionDto reportDefinition) {
    final Optional<RoleType> authorizedRole =
        reportAuthorizationService.getAuthorizedRole(userId, reportDefinition);
    return authorizedRole
        .filter(roleType -> roleType.ordinal() >= RoleType.EDITOR.ordinal())
        .map(role -> new AuthorizedReportDefinitionResponseDto(reportDefinition, role))
        .orElseThrow(
            () ->
                new ForbiddenException(
                    "User ["
                        + userId
                        + "] is not authorized to edit report ["
                        + reportDefinition.getName()
                        + "]."));
  }

  public Set<ConflictedItemDto> getConflictedItemsFromReportDefinition(
      final String userId, final String reportId) {
    final ReportDefinitionDto reportDefinitionDto =
        getReportDefinition(reportId, userId).getDefinitionDto();
    return getConflictedItemsForDeleteReport(reportDefinitionDto);
  }

  public void validateReportDescription(final String reportDescription) {
    if (reportDescription != null) {
      if (reportDescription.length() > 400) {
        throw new OptimizeValidationException(
            "Report descriptions cannot be greater than 400 characters");
      } else if (reportDescription.isEmpty()) {
        throw new OptimizeValidationException("Report descriptions cannot be non-null and empty");
      }
    }
  }

  public Optional<String> updateReportDefinitionXmlIfRequiredAndReturn(
      final ReportDefinitionDto reportDefinition) {
    // we only need to validate that the stored XML is still up to date for heatmap reports on the
    // latest or all versions to
    // ensure the report result is visualised correctly in the UI
    if (reportDefinition.getData() instanceof final ProcessReportDataDto reportData
        && isHeatmapReportOnVersionAllOrLatest(reportData)) {
      // retrieve latest version of definition which is cached in definitionService
      final Optional<String> latestXML =
          defintionService
              .getDefinitionWithXmlAsService(
                  DefinitionType.PROCESS,
                  reportData.getDefinitionKey(),
                  List.of(LATEST_VERSION),
                  reportData.getTenantIds())
              .map(ProcessDefinitionOptimizeDto.class::cast)
              .map(ProcessDefinitionOptimizeDto::getBpmn20Xml);
      if (latestXML.isPresent()
          && !latestXML.get().equals(reportData.getConfiguration().getXml())) {
        updateDefinitionXmlOfProcessReports(reportData.getProcessDefinitionKey(), latestXML.get());
        return latestXML;
      }
    }
    return Optional.empty();
  }

  private IdResponseDto copyAndMoveReport(
      final ReportDefinitionDto originalReportDefinition,
      final String userId,
      final String newReportName,
      final String newCollectionId) {
    final String oldCollectionId = originalReportDefinition.getCollectionId();
    validateEntityEditorAuthorization(oldCollectionId);

    switch (originalReportDefinition.getReportType()) {
      case PROCESS:
        final ProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
            (ProcessReportDefinitionRequestDto) originalReportDefinition;
        ensureCompliesWithCollectionScope(
            userId, newCollectionId, singleProcessReportDefinitionDto);
        return reportWriter.createNewSingleProcessReport(
            userId,
            singleProcessReportDefinitionDto.getData(),
            newReportName,
            originalReportDefinition.getDescription(),
            newCollectionId);
      case DECISION:
        final DecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
            (DecisionReportDefinitionRequestDto) originalReportDefinition;
        ensureCompliesWithCollectionScope(
            userId, newCollectionId, singleDecisionReportDefinitionDto);
        return reportWriter.createNewSingleDecisionReport(
            userId,
            singleDecisionReportDefinitionDto.getData(),
            newReportName,
            originalReportDefinition.getDescription(),
            newCollectionId);
      default:
        throw new IllegalStateException(
            "Unsupported reportType: " + originalReportDefinition.getReportType());
    }
  }

  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(
      final ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    conflictedItems.addAll(
        reportRelationService.getConflictedItemsForDeleteReport(reportDefinition));
    return conflictedItems;
  }

  public void ensureCompliesWithCollectionScope(
      final String userId, final String collectionId, final String reportId) {
    final ReportDefinitionDto reportDefinition =
        reportReader
            .getReport(reportId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Was not able to retrieve report with id ["
                            + reportId
                            + "]"
                            + "from Elasticsearch. Report does not exist."));

    final ReportDefinitionDto<?> singleProcessReportDefinitionDto =
        (ReportDefinitionDto<?>) reportDefinition;
    ensureCompliesWithCollectionScope(userId, collectionId, singleProcessReportDefinitionDto);
  }

  public void ensureCompliesWithCollectionScope(
      final List<ReportDataDefinitionDto> definitions,
      final DefinitionType definitionType,
      final CollectionDefinitionDto collection) {
    definitions.forEach(
        definitionDto ->
            ensureCompliesWithCollectionScope(
                definitionDto.getKey(), definitionDto.getTenantIds(), definitionType, collection));
  }

  public boolean isReportAllowedForCollectionScope(
      final ReportDefinitionDto<?> report, final CollectionDefinitionDto collection) {
    return report.getData().getDefinitions().stream()
        .allMatch(
            definitionDto ->
                COMPLIANT.equals(
                    getScopeComplianceForReport(
                        definitionDto.getKey(),
                        definitionDto.getTenantIds(),
                        report.getReportType().toDefinitionType(),
                        collection)));
  }

  private void ensureCompliesWithCollectionScope(
      final CollectionDefinitionDto collection, final ReportDefinitionDto<?> report) {
    ensureCompliesWithCollectionScope(
        report.getData().getDefinitions(), report.getDefinitionType(), collection);
  }

  private void ensureCompliesWithCollectionScope(
      final String userId,
      final String collectionId,
      final ReportDefinitionDto<?> definition) {
    if (collectionId == null) {
      return;
    }

    final CollectionDefinitionDto collection =
        collectionService
            .getAuthorizedCollectionDefinitionOrFail(userId, collectionId)
            .getDefinitionDto();

    ensureCompliesWithCollectionScope(collection, definition);
  }

  private void ensureCompliesWithCollectionScope(
      final String definitionKey,
      final List<String> tenantIds,
      final DefinitionType definitionType,
      final CollectionDefinitionDto collection) {
    final ScopeComplianceType complianceLevel =
        getScopeComplianceForReport(definitionKey, tenantIds, definitionType, collection);
    if (NON_TENANT_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto =
          new ConflictedItemDto(
              collection.getId(), ConflictedItemType.COLLECTION, collection.getName());
      throw new OptimizeNonTenantScopeCompliantException(Set.of(conflictedItemDto));
    } else if (NON_DEFINITION_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto =
          new ConflictedItemDto(
              collection.getId(), ConflictedItemType.COLLECTION, collection.getName());
      throw new OptimizeNonDefinitionScopeCompliantException(Set.of(conflictedItemDto));
    }
  }

  private void validateEntityEditorAuthorization(final String collectionId) {
    if (collectionId == null
        && !identityService.getEnabledAuthorizations().contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User is not an authorized entity editor");
    }
  }

  private ScopeComplianceType getScopeComplianceForReport(
      final String definitionKey,
      final List<String> tenantIds,
      final DefinitionType definitionType,
      final CollectionDefinitionDto collection) {
    if (definitionKey == null) {
      return COMPLIANT;
    }

    final List<ScopeComplianceType> compliances =
        collection.getData().getScope().stream()
            .map(scope -> scope.getComplianceType(definitionType, definitionKey, tenantIds))
            .collect(toList());

    final boolean scopeCompliant =
        compliances.stream().anyMatch(compliance -> compliance.equals(COMPLIANT));
    if (scopeCompliant) {
      return COMPLIANT;
    }
    final boolean definitionCompliantButNonTenantCompliant =
        compliances.stream().anyMatch(compliance -> compliance.equals(NON_TENANT_COMPLIANT));
    if (definitionCompliantButNonTenantCompliant) {
      return NON_TENANT_COMPLIANT;
    }
    return NON_DEFINITION_COMPLIANT;
  }

  private void checkForUpdateConflictsOnSingleProcessDefinition(
      final ProcessReportDefinitionRequestDto currentReportVersion,
      final ProcessReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    conflictedItems.addAll(
        reportRelationService.getConflictedItemsForUpdatedReport(
            currentReportVersion, reportUpdateDto));

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(
      final DecisionReportDefinitionRequestDto currentReportVersion,
      final DecisionReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems =
        reportRelationService.getConflictedItemsForUpdatedReport(
            currentReportVersion, reportUpdateDto);

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private SingleProcessReportDefinitionUpdateDto convertToSingleProcessReportUpdate(
      final ProcessReportDefinitionRequestDto updatedReport, final String userId) {
    final SingleProcessReportDefinitionUpdateDto reportUpdate =
        new SingleProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDefinitionKey();
      reportUpdate
          .getData()
          .setProcessDefinitionName(
              extractProcessDefinitionName(definitionKey, xml).orElse(definitionKey));
    }
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(
      final DecisionReportDefinitionRequestDto updatedReport, final String userId) {

    final SingleDecisionReportDefinitionUpdateDto reportUpdate =
        new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDecisionDefinitionKey();
      reportUpdate
          .getData()
          .setDecisionDefinitionName(
              extractDecisionDefinitionName(definitionKey, xml).orElse(definitionKey));
    }
    return reportUpdate;
  }

  private ProcessReportDefinitionRequestDto getSingleProcessReportDefinition(
      final String reportId, final String userId) {
    final ProcessReportDefinitionRequestDto report =
        reportReader
            .getSingleProcessReportOmitXml(reportId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Single process report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException(
          "User ["
              + userId
              + "] is not authorized to access or edit report ["
              + report.getName()
              + "].");
    }
    return report;
  }

  private DecisionReportDefinitionRequestDto getSingleDecisionReportDefinition(
      final String reportId, final String userId) {
    final DecisionReportDefinitionRequestDto report =
        reportReader
            .getSingleDecisionReportOmitXml(reportId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Single decision report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException(
          "User ["
              + userId
              + "] is not authorized to access or edit report ["
              + report.getName()
              + "].");
    }
    return report;
  }

  public Set<String> filterAuthorizedReportIds(final String userId, final Set<String> reportIds) {
    final List<ReportDefinitionDto> reports =
        reportReader.getAllReportsForIdsOmitXml(new ArrayList<>(reportIds));
    return filterAuthorizedReports(userId, reports).stream()
        .map(report -> report.getDefinitionDto().getId())
        .collect(toSet());
  }

  private List<AuthorizedReportDefinitionResponseDto> filterAuthorizedReports(
      final String userId, final List<ReportDefinitionDto> reports) {
    return reports.stream()
        .map(
            report -> Pair.of(report, reportAuthorizationService.getAuthorizedRole(userId, report)))
        .filter(reportAndRole -> reportAndRole.getValue().isPresent())
        .map(
            reportAndRole ->
                new AuthorizedReportDefinitionResponseDto(
                    reportAndRole.getKey(), reportAndRole.getValue().get()))
        .collect(toList());
  }

  private boolean isManagementOrInstantPreviewReport(
      final ReportDefinitionDto<?> reportDefinition) {
    return reportDefinition instanceof ProcessReportDefinitionRequestDto
        && (((ProcessReportDefinitionRequestDto) reportDefinition)
                .getData()
                .isManagementReport()
            || ((ProcessReportDefinitionRequestDto) reportDefinition)
                .getData()
                .isInstantPreviewReport());
  }

  private boolean isHeatmapReportOnVersionAllOrLatest(final ProcessReportDataDto reportData) {
    return ProcessVisualization.HEAT.equals(reportData.getVisualization())
        && DefinitionVersionHandlingUtil.isDefinitionVersionSetToAllOrLatest(
            reportData.getDefinitionVersions());
  }

  @FunctionalInterface
  private interface CreateReportMethod<RD extends ReportDataDto> {
    IdResponseDto create(
        String userId, RD reportData, String reportName, String description, String collectionId);
  }
}
