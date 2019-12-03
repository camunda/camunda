/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
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

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
  public static final String SCOPE_NOT_AUTHORIZED_MESSAGE = "User [%s] is not authorized to add scope [%s]. Either he" +
    " isn't allowed to access the definition or the provided tenants.";

  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ReportReader reportReader;
  private final CollectionRoleService collectionRoleService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final ReportService reportService;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String identityId,
                                                              final IdentityType identityType,
                                                              final String collectionId) {
    final Map<String, TenantDto> tenantsForUserById = tenantService.getTenantsForUser(identityId)
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, tenantDto -> tenantDto));
    return collectionRoleService.getSimpleCollectionDefinitionWithRoleMetadata(identityId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .map(scope -> {
        final List<String> tenantsToMask = scope.getTenants();
        final List<TenantDto> authorizedTenantDtos = definitionAuthorizationService
          .filterAuthorizedTenantsForDefinition(
            identityId, identityType, scope.getDefinitionKey(), scope.getDefinitionType(), scope.getTenants()
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
        getDefinitionName(identityId, collectionScopeEntryRestDto)
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
    verifyUserIsAuthorizedToAccessScopesOrFail(userId, scopeUpdates);
    collectionWriter.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  private void verifyUserIsAuthorizedToAccessScopesOrFail(final String userId,
                                                          final List<CollectionScopeEntryDto> scopeEntries) {
    scopeEntries.forEach(scopeEntry -> {
      boolean isAuthorized = definitionAuthorizationService.isAuthorizedToSeeDefinition(
        userId,
        IdentityType.USER,
        scopeEntry.getDefinitionKey(),
        scopeEntry.getDefinitionType(),
        scopeEntry.getTenants()
      );
      if (!isAuthorized) {
        String message = String.format(
          SCOPE_NOT_AUTHORIZED_MESSAGE, userId, scopeEntry.getId());
        throw new ForbiddenException(message);
      }
    });
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

  private void deleteReports(final String userId,
                             final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate) {
    reportsAffectedByScopeUpdate
      .stream()
      .map(SingleReportDefinitionDto::getId)
      .forEach(reportId -> reportService.deleteReport(userId, reportId, true));
  }

  public Set<ConflictedItemDto> getAllConflictsOnScopeDeletion(final String userId,
                                                               final String collectionId,
                                                               final String scopeId) {
    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion =
      getAllReportsAffectedByScopeDeletion(collectionId, scopeId);
    return getConflictsForReports(userId, reportsAffectedByScopeDeletion);
  }

  private void checkForConflictsOnScopeDeletion(final String userId,
                                                final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion) {
    Set<ConflictedItemDto> conflictedItems =
      getConflictsForReports(userId, reportsAffectedByScopeDeletion);
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeConflictException(conflictedItems);
    }
  }

  private Set<ConflictedItemDto> getConflictsForReports(final String userId,
                                                        final List<SingleReportDefinitionDto<?>> reports) {
    return reports
      .stream()
      .flatMap(report -> {
        Set<ConflictedItemDto> reportConflicts =
          reportService.getReportDeleteConflictingItems(userId, report.getId()).getConflictedItems();
        reportConflicts.add(this.reportToConflictedItem(report));
        return reportConflicts.stream();
      })
      .collect(Collectors.toSet());
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

  public void updateScopeEntry(final String userId,
                               final String collectionId,
                               final CollectionScopeEntryUpdateDto scopeUpdate,
                               final String scopeEntryId,
                               boolean force) {
    final SimpleCollectionDefinitionDto collectionDefinition =
      authorizedCollectionService
        .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId)
        .getDefinitionDto();
    final CollectionScopeEntryDto currentScope = getScopeOfCollection(scopeEntryId, collectionDefinition);
    replaceMaskedTenantsInUpdateWithRealOnes(userId, scopeUpdate, currentScope);
    final Set<String> tenantsThatWillBeRemoved = retrieveRemovedTenants(scopeUpdate, currentScope);

    updateScopeInCollection(scopeEntryId, scopeUpdate, collectionDefinition);
    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate =
      getReportsAffectedByScopeUpdate(collectionId, collectionDefinition);

    if (!force) {
      checkForConflictOnUpdate(userId, reportsAffectedByScopeUpdate);
    }

    updateReportsWithNewTenants(userId, tenantsThatWillBeRemoved, reportsAffectedByScopeUpdate);
    collectionWriter.updateScopeEntity(collectionId, scopeUpdate, userId, scopeEntryId);
  }

  private Set<String> retrieveRemovedTenants(final CollectionScopeEntryUpdateDto scopeUpdate,
                                             final CollectionScopeEntryDto currentScope) {
    final Set<String> tenantsToBeRemoved = new HashSet<>(currentScope.getTenants());
    tenantsToBeRemoved.removeAll(scopeUpdate.getTenants());
    return tenantsToBeRemoved;
  }

  private void updateReportsWithNewTenants(final String userId,
                                           final Set<String> tenantsToBeRemoved,
                                           final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate) {

    final Map<ReportType, List<SingleReportDefinitionDto<?>>> byDefinitionType = reportsAffectedByScopeUpdate
      .stream()
      .peek(r -> {
        r.getData().getTenantIds().removeAll(tenantsToBeRemoved);
        if (r.getData().getTenantIds().isEmpty()) {
          reportService.deleteReport(userId, r.getId(), true);
        }
      })
      .filter(r -> !r.getData().getTenantIds().isEmpty())
      .collect(Collectors.groupingBy(SingleReportDefinitionDto::getReportType));
    byDefinitionType.getOrDefault(ReportType.DECISION, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleDecisionReportDefinitionDto)
      .map(r -> (SingleDecisionReportDefinitionDto) r)
      .forEach(r -> reportService.updateSingleDecisionReport(r.getId(), r, userId, true));
    byDefinitionType.getOrDefault(ReportType.PROCESS, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleProcessReportDefinitionDto)
      .map(r -> (SingleProcessReportDefinitionDto) r)
      .forEach(r -> reportService.updateSingleProcessReport(r.getId(), r, userId, true));
  }

  private CollectionScopeEntryDto getScopeOfCollection(final String scopeEntryId,
                                                       final SimpleCollectionDefinitionDto collectionDefinition) {
    return collectionDefinition
      .getData()
      .getScope()
      .stream()
      .filter(scope -> scope.getId().equals(scopeEntryId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException(String.format(
        "Unknown scope entry for collection [%s] and scope [%s]",
        collectionDefinition.getId(),
        scopeEntryId
      )));
  }

  private void checkForConflictOnUpdate(final String userId,
                                        final List<SingleReportDefinitionDto<?>> reportsAffectedByUpdate) {
    Set<ConflictedItemDto> conflictedItems = getConflictsForReports(userId, reportsAffectedByUpdate);
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

  private void updateScopeInCollection(final String scopeEntryId,
                                       final CollectionScopeEntryUpdateDto scopeUpdate,
                                       final SimpleCollectionDefinitionDto collectionDefinition) {
    getScopeOfCollection(scopeEntryId, collectionDefinition)
      .setTenants(scopeUpdate.getTenants());
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

  public Map<String, List<String>> getAvailableKeysAndTenantsFromCollectionScope(final String identityId,
                                                                                 final IdentityType identityType,
                                                                                 final String collectionId) {
    if (collectionId == null) {
      return Collections.emptyMap();
    }
    return getAuthorizedCollectionScopeEntries(identityId, identityType, collectionId)
      .stream()
      .map(scopeEntryDto -> new AbstractMap.SimpleEntry<>(scopeEntryDto.getDefinitionKey(), scopeEntryDto.getTenants()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<CollectionScopeEntryDto> getAuthorizedCollectionScopeEntries(final String identityId,
                                                                            final IdentityType identityType,
                                                                            final String collectionId) {
    return collectionRoleService.getSimpleCollectionDefinitionWithRoleMetadata(identityId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .peek(scope -> scope.setTenants(
        definitionAuthorizationService
          .filterAuthorizedTenantsForDefinition(
            identityId, identityType, scope.getDefinitionKey(), scope.getDefinitionType(), scope.getTenants()
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
