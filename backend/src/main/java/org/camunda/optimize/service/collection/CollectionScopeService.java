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
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
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

  private final CollectionService collectionService;
  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ReportReader reportReader;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String userId,
                                                              final String collectionId) {
    final Map<String, TenantDto> tenantsForUserById = tenantService.getTenantsForUser(userId)
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, tenantDto -> tenantDto));
    return collectionService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
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
          .setDefinitionName(getDefinitionName(userId, scope))
          .setDefinitionType(scope.getDefinitionType())
          .setTenants(authorizedTenantDtos);
      })
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(collectionScopeEntryRestDto -> collectionScopeEntryRestDto.getTenants()
        .stream()
        .anyMatch(t -> !UNAUTHORIZED_TENANT_MASK_ID.equals(t.getId())))
      .sorted(
        Comparator.comparing(CollectionScopeEntryRestDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryRestDto::getDefinitionName)
      )
      .collect(Collectors.toList());
  }

  public CollectionScopeEntryDto addScopeEntryToCollection(String userId,
                                                           String collectionId,
                                                           CollectionScopeEntryDto entryDto)
    throws OptimizeCollectionConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    return collectionWriter.addScopeEntryToCollection(collectionId, entryDto, userId);
  }

  public void removeScopeEntry(String userId, String collectionId, String scopeEntryId)
    throws NotFoundException, OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    List<ReportDefinitionDto> entities = reportReader.findReportsForCollectionOmitXml(collectionId);
    CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(scopeEntryId);

    List<ReportDefinitionDto> conflictedItems = entities.stream()
      .filter(report -> report.getData() instanceof SingleReportDataDto)
      .filter(report ->
                ((SingleReportDataDto) report.getData()).getDefinitionKey().equals(scopeEntry.getDefinitionKey())
                  && report.getReportType().toString().equalsIgnoreCase(scopeEntry.getDefinitionType().getId()))
      .collect(Collectors.toList());

    if (conflictedItems.size() == 0) {
      collectionWriter.removeScopeEntry(collectionId, scopeEntryId, userId);
    } else {
      throw new OptimizeConflictException(
        conflictedItems.stream().map(this::reportToConflictedItem).collect(Collectors.toSet())
      );
    }
  }

  public void updateScopeEntry(String userId,
                               String collectionId,
                               CollectionScopeEntryUpdateDto scopeUpdate,
                               String scopeEntryId) {
    final AuthorizedSimpleCollectionDefinitionDto authorizedCollection =
      authorizedCollectionService
        .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    final CollectionScopeEntryDto currentScope = authorizedCollection.getDefinitionDto()
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

    replaceMaskedTenantsWithRealOnes(userId, scopeUpdate, currentScope);
    collectionWriter.updateScopeEntity(collectionId, scopeUpdate, userId, scopeEntryId);
  }

  private void replaceMaskedTenantsWithRealOnes(final String userId,
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
    return collectionService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
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

  private String getDefinitionName(final String userId, final CollectionScopeEntryDto scope) {
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
