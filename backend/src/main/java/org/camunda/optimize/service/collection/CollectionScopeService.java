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
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionScopeService {

  private final CollectionService collectionService;
  private final TenantService tenantService;
  private final DefinitionAuthorizationService definitionAuthorizationService;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String userId,
                                                              final String collectionId) {
    return collectionService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .filter(
        scope ->
          definitionAuthorizationService
            .isAuthorizedToSeeDefinitionWithAtLeastOneTenantAuthorized(
              userId,
              scope.getDefinitionKey(),
              scope.getDefinitionType(),
              scope.getTenants()
            )
      )
      .map(scope -> mapScopeEntryToRestDto(userId, scope))
      .sorted(
        Comparator.comparing(CollectionScopeEntryRestDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryRestDto::getDefinitionName)
      )
      .collect(Collectors.toList());
  }

  private CollectionScopeEntryRestDto mapScopeEntryToRestDto(final String userId,
                                                             final CollectionScopeEntryDto scope) {

    final List<TenantDto> tenants = tenantService.getTenantsForUser(userId)
      .stream()
      .filter(t -> scope.getTenants().contains(t.getId()))
      .collect(Collectors.toList());
    return new CollectionScopeEntryRestDto()
      .setDefinitionKey(scope.getDefinitionKey())
      .setDefinitionName(scope.getDefinitionKey())
      .setDefinitionType(scope.getDefinitionType())
      .setTenants(tenants);
  }
}
