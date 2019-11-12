/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionScopeService {

  private final CollectionService collectionService;
  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String userId,
                                                              final String collectionId) {
    final Map<String, TenantDto> tenantsForUserById = tenantService.getTenantsForUser(userId)
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, tenantDto -> tenantDto));
    return getAuthorizedCollectionScopeEntries(userId, collectionId)
      .stream()
      .map(scope -> {
        final List<TenantDto> authorizedTenantDtos = scope.getTenants()
          .stream()
          .map(tenantsForUserById::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
        return new CollectionScopeEntryRestDto()
          .setId(scope.getId())
          .setDefinitionKey(scope.getDefinitionKey())
          .setDefinitionName(getDefinitionName(userId, scope))
          .setDefinitionType(scope.getDefinitionType())
          .setTenants(authorizedTenantDtos);
      })
      .sorted(
        Comparator.comparing(CollectionScopeEntryRestDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryRestDto::getDefinitionName)
      )
      .collect(Collectors.toList());
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

}
