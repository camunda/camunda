/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionScopeService {

  private static final String UNAUTHORIZED_TENANT_MASK_NAME = "Unauthorized Tenant";
  private static final String UNAUTHORIZED_TENANT_MASK_ID = "__unauthorizedTenantId__";
  public static final TenantDto UNAUTHORIZED_TENANT_MASK =
    new TenantDto(UNAUTHORIZED_TENANT_MASK_ID, UNAUTHORIZED_TENANT_MASK_NAME, "unknownEngine");

  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ReportReader reportReader;
  private final CollectionRoleService collectionRoleService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final ReportService reportService;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String userId,
                                                              final String collectionId) {
    final Map<String, TenantDto> tenantsForUserById = tenantService.getTenantsForUser(userId)
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, tenantDto -> tenantDto));
    return collectionRoleService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .map(scope -> {
        final List<String> tenantsToMask = scope.getTenants();
        final List<TenantDto> authorizedTenantDtos = definitionAuthorizationService
          .filterAuthorizedTenantsForDefinition(
            userId, scope.getDefinitionKey(), scope.getDefinitionType(), scope.getTenants()
          )
          .stream()
          .peek(tenantsToMask::remove)
          .map(tenantsForUserById::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
        authorizedTenantDtos.addAll(tenantsToMask.stream()
                                      .map((t) -> UNAUTHORIZED_TENANT_MASK)
                                      .collect(Collectors.toList()));
        return new CollectionScopeEntryRestDto()
          .setId(scope.getId())
          .setDefinitionKey(scope.getDefinitionKey())
          .setDefinitionType(scope.getDefinitionType())
          .setTenants(authorizedTenantDtos);
      })
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(collectionScopeEntryRestDto -> collectionScopeEntryRestDto.getTenants()
        .stream()
        .anyMatch(t -> !UNAUTHORIZED_TENANT_MASK_ID.equals(t.getId())))
      // for all visible entries we need to resolve the actual definition name
      // we do it only after the filtering as only then it is ensured the user has access to that entry at all
      .peek(collectionScopeEntryRestDto -> collectionScopeEntryRestDto.setDefinitionName(
        getDefinitionName(userId, collectionScopeEntryRestDto)
      ))
      .sorted(
        Comparator.comparing(CollectionScopeEntryRestDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryRestDto::getDefinitionName)
      )
      .collect(Collectors.toList());
  }

  public void addScopeEntriesToCollection(final String userId,
                                          final String collectionId,
                                          final List<CollectionScopeEntryDto> scopeUpdates) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  public void deleteScopeEntry(String userId, String collectionId, String scopeEntryId, boolean force)
    throws NotFoundException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion =
      getAllReportsAffectedByScopeDeletion(collectionId, scopeEntryId);
    if (!force) {
      checkForConflictsOnScopeDeletion(userId, reportsAffectedByScopeDeletion);
    }

    deleteReports(userId, reportsAffectedByScopeDeletion);
    collectionWriter.removeScopeEntry(collectionId, scopeEntryId, userId);
  }

  private void checkForConflictsOnScopeDeletion(final String userId,
                                                final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion) {
    Set<ConflictedItemDto> conflictedItems =
      reportsAffectedByScopeDeletion
      .stream()
      .flatMap(report -> retrieveAllReportConflicts(userId, report))
      .collect(Collectors.toSet());
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private List<SingleReportDefinitionDto<?>> getAllReportsAffectedByScopeDeletion(final String collectionId,
                                                                                  final String scopeEntryId) {
    CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(scopeEntryId);
    List<ReportDefinitionDto> reportsInCollection = reportReader.findReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.getCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> reportInSameScopeAsGivenScope(scopeEntry, report))
      .collect(Collectors.toList());
  }

  private boolean reportInSameScopeAsGivenScope(final CollectionScopeEntryDto scopeEntry, final SingleReportDefinitionDto<?> report) {
    final CollectionScopeEntryDto scopeOfReport =
      new CollectionScopeEntryDto(report.getDefinitionType(), report.getData().getDefinitionKey());
    return scopeOfReport.equals(scopeEntry);
  }

  private Stream<? extends ConflictedItemDto> retrieveAllReportConflicts(final String userId,
                                                                         final SingleReportDefinitionDto<?> report) {
    Set<ConflictedItemDto> reportConflicts =
      reportService.getReportDeleteConflictingItems(userId, report.getId()).getConflictedItems();
    reportConflicts.add(this.reportToConflictedItem(report));
    return reportConflicts.stream();
  }

  public void updateScopeEntry(final String userId,
                               final String collectionId,
                               final CollectionScopeEntryUpdateDto scopeUpdate,
                               final String scopeEntryId,
                               boolean force) {
    final SimpleCollectionDefinitionDto collectionDefinition =
      authorizedCollectionService
        .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId)
        .getDefinitionDto();
    final CollectionScopeEntryDto currentScope = getScopeOfCollection(collectionId, scopeEntryId, collectionDefinition);
    replaceMaskedTenantsInUpdateWithRealOnes(userId, scopeUpdate, currentScope);

    // this will also adjust the current scope in the collectionDefinition from above
    addScopeUpdateToCurrentScope(currentScope, scopeUpdate);
    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate =
      getReportsAffectedByScopeUpdate(collectionId, collectionDefinition);

    if (!force) {
      checkForConflictOnUpdate(userId, reportsAffectedByScopeUpdate);
    }

    deleteReports(userId, reportsAffectedByScopeUpdate);
    collectionWriter.updateScopeEntity(collectionId, scopeUpdate, userId, scopeEntryId);
  }

  private void deleteReports(final String userId,
                             final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate) {
    reportsAffectedByScopeUpdate
      .stream()
      .map(SingleReportDefinitionDto::getId)
      .forEach(reportId -> reportService.deleteReport(userId, reportId, true));
  }

  private CollectionScopeEntryDto getScopeOfCollection(final String collectionId,
                                                       final String scopeEntryId,
                                                       final SimpleCollectionDefinitionDto collectionDefinition) {
    return collectionDefinition
      .getData()
      .getScope()
      .stream()
      .filter(scope -> scope.getId().equals(scopeEntryId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException(String.format(
        "Unknown scope entry for collection [%s] and scope [%s]",
        collectionId,
        scopeEntryId
      )));
  }

  private void checkForConflictOnUpdate(final String userId,
                                        final List<SingleReportDefinitionDto<?>> reportsAffectedByUpdate) {
    Set<ConflictedItemDto> conflictedItems = reportsAffectedByUpdate.stream()
      .flatMap(report -> retrieveAllReportConflicts(userId, report))
      .collect(Collectors.toSet());
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private List<SingleReportDefinitionDto<?>> getReportsAffectedByScopeUpdate(final String collectionId,
                                                                             final SimpleCollectionDefinitionDto collectionDefinition) {
    List<ReportDefinitionDto> reportsInCollection = reportReader.findReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.getCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> !reportService.isReportAllowedForCollectionScope(report, collectionDefinition))
      .collect(Collectors.toList());
  }

  private void addScopeUpdateToCurrentScope(final CollectionScopeEntryDto currentScope,
                                            final CollectionScopeEntryUpdateDto scopeUpdate) {
    currentScope.setTenants(scopeUpdate.getTenants());
  }

  private void replaceMaskedTenantsInUpdateWithRealOnes(final String userId,
                                                        final CollectionScopeEntryUpdateDto scopeUpdate,
                                                        final CollectionScopeEntryDto currentScope) {
    final List<String> unauthorizedTenantsOfCurrentScope = currentScope.getTenants()
      .stream()
      .filter(tenant -> !tenantService.isAuthorizedToSeeTenant(userId, tenant))
      .collect(Collectors.toList());
    final List<String> allTenants = tenantService.getTenants()
      .stream()
      .map(TenantDto::getId)
      .collect(Collectors.toList());
    final List<String> allTenantsWithMaskedTenantsBeingResolved =
      Stream.concat(
        scopeUpdate.getTenants().stream().filter(allTenants::contains),
        unauthorizedTenantsOfCurrentScope.stream()
      )
        .distinct()
        .collect(Collectors.toList());
    scopeUpdate.setTenants(allTenantsWithMaskedTenantsBeingResolved);
  }

  public Map<String, List<String>> getAvailableKeysAndTenantsFromCollectionScope(final String userId,
                                                                                 final String collectionId) {
    if (collectionId == null) {
      return Collections.emptyMap();
    }
    return getAuthorizedCollectionScopeEntries(userId, collectionId)
      .stream()
      .map(scopeEntryDto -> new AbstractMap.SimpleEntry<>(scopeEntryDto.getDefinitionKey(), scopeEntryDto.getTenants()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<CollectionScopeEntryDto> getAuthorizedCollectionScopeEntries(final String userId,
                                                                            final String collectionId) {
    return collectionRoleService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .peek(scope -> scope.setTenants(
        definitionAuthorizationService
          .filterAuthorizedTenantsForDefinition(
            userId, scope.getDefinitionKey(), scope.getDefinitionType(), scope.getTenants()
          )
      ))
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(scopeEntryDto -> scopeEntryDto.getTenants().size() > 0)
      .collect(Collectors.toList());
  }

  private String getDefinitionName(final String userId, final CollectionScopeEntryRestDto scope) {
    return definitionService.getDefinition(scope.getDefinitionType(), scope.getDefinitionKey(), userId)
      .map(DefinitionWithTenantsDto::getName)
      .orElse(scope.getDefinitionKey());
  }

  private ConflictedItemDto reportToConflictedItem(CollectionEntity collectionEntity) {
    return new ConflictedItemDto(
      collectionEntity.getId(),
      ConflictedItemType.REPORT,
      collectionEntity.getName()
    );
  }

}
