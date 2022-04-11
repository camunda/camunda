/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.collection;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryResponseDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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
  public static final String SCOPE_NOT_AUTHORIZED_MESSAGE = "User [%s] is not authorized to add scope [%s]. Either " +
    "they aren't allowed to access the definition or the provided tenants.";

  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final ReportReader reportReader;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final ReportService reportService;

  public List<CollectionScopeEntryResponseDto> getCollectionScope(final String userId,
                                                                  final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .map(scope -> {
        final List<TenantDto> authorizedTenantDtos = resolveAuthorizedTenantsForScopeEntry(userId, scope);

        final List<String> unauthorizedTenantsIds = scope.getTenants();
        authorizedTenantDtos.stream().map(TenantDto::getId).forEach(unauthorizedTenantsIds::remove);

        authorizedTenantDtos.addAll(
          unauthorizedTenantsIds.stream().map((t) -> UNAUTHORIZED_TENANT_MASK).collect(Collectors.toList())
        );
        return CollectionScopeEntryResponseDto.from(scope, authorizedTenantDtos);
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
        Comparator.comparing(CollectionScopeEntryResponseDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryResponseDto::getDefinitionName)
      )
      .collect(Collectors.toList());
  }

  public List<DefinitionResponseDto> getCollectionDefinitions(final DefinitionType definitionType,
                                                              final String userId,
                                                              final String collectionId) {
    final Map<String, List<String>> keysAndTenants =
      getAvailableKeysAndTenantsFromCollectionScope(userId, definitionType, collectionId);

    if (keysAndTenants.isEmpty()) {
      return Collections.emptyList();
    }

    return definitionService.getFullyImportedDefinitions(
      definitionType,
      keysAndTenants.keySet(),
      keysAndTenants.values().stream().flatMap(List::stream).collect(Collectors.toList()),
      userId
    );
  }

  public List<DefinitionVersionResponseDto> getCollectionDefinitionVersionsByKeyAndType(final DefinitionType type,
                                                                                        final String key,
                                                                                        final String userId,
                                                                                        final String collectionId) {
    final Optional<CollectionScopeEntryDto> optionalScopeEntry = getCollectionScopeEntryDtoStream(userId, collectionId)
      .filter(entry -> entry.getDefinitionType().equals(type) && entry.getDefinitionKey().equals(key))
      .findFirst();

    if (!optionalScopeEntry.isPresent()) {
      return Collections.emptyList();
    }

    return definitionService.getDefinitionVersions(type, key, userId, optionalScopeEntry.get().getTenants());
  }

  public List<TenantDto> getCollectionDefinitionTenantsByKeyAndType(final DefinitionType type,
                                                                    final String key,
                                                                    final String userId,
                                                                    final List<String> versions,
                                                                    final String collectionId) {
    final Optional<CollectionScopeEntryDto> optionalScopeEntry = getCollectionScopeEntryDtoStream(userId, collectionId)
      .filter(entry -> entry.getDefinitionType().equals(type) && entry.getDefinitionKey().equals(key))
      .findFirst();

    if (!optionalScopeEntry.isPresent()) {
      return Collections.emptyList();
    }

    final Set<String> scopeTenantIds = Sets.newHashSet(optionalScopeEntry.get().getTenants());
    final Supplier<String> latestVersionAvailableInScopeSupplier = () ->
      definitionService.getDefinitionVersions(type, key, userId, optionalScopeEntry.get().getTenants())
        .stream()
        .findFirst()
        .map(DefinitionVersionResponseDto::getVersion)
        .orElseThrow(() -> new OptimizeValidationException("Could not resolve latest version."));

    return definitionService.getDefinitionTenants(type, key, userId, versions, latestVersionAvailableInScopeSupplier)
      .stream()
      .filter(tenantDto -> scopeTenantIds.contains(tenantDto.getId()))
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
      boolean isAuthorized = definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, scopeEntry.getDefinitionType(), scopeEntry.getDefinitionKey(), scopeEntry.getTenants()
      );
      if (!isAuthorized) {
        final String message = String.format(SCOPE_NOT_AUTHORIZED_MESSAGE, userId, scopeEntry.getId());
        throw new ForbiddenException(message);
      }
    });
  }

  public void deleteScopeEntry(String userId, String collectionId, String scopeEntryId, boolean force) {
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
      .forEach(reportId -> reportService.deleteReportAsUser(userId, reportId, true));
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
      throw new OptimizeCollectionConflictException(conflictedItems);
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
    final CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(scopeEntryId);
    final List<ReportDefinitionDto> reportsInCollection = reportReader.getReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.isCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> reportInSameScopeAsGivenScope(scopeEntry, report))
      .collect(Collectors.toList());
  }

  private boolean reportInSameScopeAsGivenScope(final CollectionScopeEntryDto scopeEntry,
                                                final SingleReportDefinitionDto<?> report) {
    return report.getData().getDefinitions().stream()
      .map(definition -> new CollectionScopeEntryDto(report.getDefinitionType(), definition.getKey()))
      .anyMatch(entry -> entry.equals(scopeEntry));
  }

  public void updateScopeEntry(final String userId,
                               final String collectionId,
                               final CollectionScopeEntryUpdateDto scopeUpdate,
                               final String scopeEntryId,
                               boolean force) {
    final CollectionDefinitionDto collectionDefinition =
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
      checkForConflictOnUpdate(reportsAffectedByScopeUpdate);
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
      .peek(r -> r.getData().getTenantIds().removeAll(tenantsToBeRemoved))
      .collect(Collectors.groupingBy(SingleReportDefinitionDto::getReportType));
    byDefinitionType.getOrDefault(ReportType.DECISION, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleDecisionReportDefinitionRequestDto)
      .map(r -> (SingleDecisionReportDefinitionRequestDto) r)
      .forEach(r -> reportService.updateSingleDecisionReport(r.getId(), r, userId, true));
    byDefinitionType.getOrDefault(ReportType.PROCESS, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleProcessReportDefinitionRequestDto)
      .map(r -> (SingleProcessReportDefinitionRequestDto) r)
      .forEach(r -> reportService.updateSingleProcessReport(r.getId(), r, userId, true));
  }

  private CollectionScopeEntryDto getScopeOfCollection(final String scopeEntryId,
                                                       final CollectionDefinitionDto collectionDefinition) {
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

  private void checkForConflictOnUpdate(final List<SingleReportDefinitionDto<?>> reportsAffectedByUpdate) {
    Set<ConflictedItemDto> conflictedItems =
      reportsAffectedByUpdate.stream()
        .map(this::reportToConflictedItem)
        .collect(Collectors.toSet());
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeCollectionConflictException(conflictedItems);
    }
  }

  public boolean hasConflictsForCollectionScopeDelete(String userId, String collectionId,
                                                      List<String> collectionScopeIds) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    return collectionScopeIds.stream()
      .anyMatch(scopeEntryId -> !getAllConflictsOnScopeDeletion(userId, collectionId, scopeEntryId).isEmpty());
  }

  public void bulkDeleteCollectionScopes(String userId, String collectionId, List<String> collectionScopeIds) {
    List<String> collectionScopesToDelete = new ArrayList<>();
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    for (String collectionScopeId : collectionScopeIds) {
      final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion =
        getAllReportsAffectedByScopeDeletion(collectionId, collectionScopeId);
      try {
        deleteReports(userId, reportsAffectedByScopeDeletion);
        collectionScopesToDelete.add(collectionScopeId);
      } catch (OptimizeRuntimeException e) {
        log.debug(
          "There was an error while deleting reports associated to collection scope with id {}. The scope cannot be " +
            "deleted.",
          collectionScopeId
        );
      }
    }
    collectionWriter.removeScopeEntries(collectionId, collectionScopesToDelete, userId);
  }

  private List<SingleReportDefinitionDto<?>> getReportsAffectedByScopeUpdate(final String collectionId,
                                                                             final CollectionDefinitionDto collectionDefinition) {
    List<ReportDefinitionDto> reportsInCollection = reportReader.getReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.isCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> !reportService.isReportAllowedForCollectionScope(report, collectionDefinition))
      .collect(Collectors.toList());
  }

  private void updateScopeInCollection(final String scopeEntryId,
                                       final CollectionScopeEntryUpdateDto scopeUpdate,
                                       final CollectionDefinitionDto collectionDefinition) {
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

  public Map<String, List<String>> getAvailableKeysAndTenantsFromCollectionScope(final String userId,
                                                                                 final DefinitionType definitionType,
                                                                                 final String collectionId) {
    if (collectionId == null) {
      return Collections.emptyMap();
    }
    return getCollectionScopeEntryDtoStream(userId, collectionId)
      .filter(scopeEntryDto -> definitionType.equals(scopeEntryDto.getDefinitionType()))
      .map(scopeEntryDto -> new AbstractMap.SimpleEntry<>(scopeEntryDto.getDefinitionKey(), scopeEntryDto.getTenants()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Stream<CollectionScopeEntryDto> getCollectionScopeEntryDtoStream(final String userId,
                                                                           final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .peek(scope -> scope.setTenants(
        resolveAuthorizedTenantsForScopeEntry(userId, scope).stream().map(TenantDto::getId).collect(Collectors.toList())
      ))
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(scopeEntryDto -> !scopeEntryDto.getTenants().isEmpty());
  }

  private List<TenantDto> resolveAuthorizedTenantsForScopeEntry(final String userId,
                                                                final CollectionScopeEntryDto scope) {
    try {
      return definitionService
        .getDefinitionWithAvailableTenants(scope.getDefinitionType(), scope.getDefinitionKey(), userId)
        .map(DefinitionResponseDto::getTenants)
        .orElseGet(ArrayList::new)
        .stream()
        .filter(tenantDto -> scope.getTenants().contains(tenantDto.getId()))
        .collect(Collectors.toList());
    } catch (ForbiddenException e) {
      return new ArrayList<>();
    }
  }

  private String getDefinitionName(final String userId, final CollectionScopeEntryResponseDto scope) {
    return definitionService.getDefinitionWithAvailableTenants(
      scope.getDefinitionType(),
      scope.getDefinitionKey(),
      userId
    )
      .map(DefinitionResponseDto::getName)
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
