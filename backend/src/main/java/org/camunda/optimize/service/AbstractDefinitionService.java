/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenants;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
abstract class AbstractDefinitionService {
  protected TenantService tenantService;
  protected DefinitionAuthorizationService definitionAuthorizationService;

  List<DefinitionAvailableVersionsWithTenants> createDefinitionsWithAvailableVersionsAndTenants(
    @NonNull final String userId,
    @NonNull final List<? extends DefinitionOptimizeDto> definitions) {
    return createDefinitionsWithAvailableVersionsAndTenants(userId, definitions, null);
  }

  List<DefinitionAvailableVersionsWithTenants> createDefinitionsWithAvailableVersionsAndTenants(
    @NonNull final String userId,
    @NonNull final List<? extends DefinitionOptimizeDto> definitions,
    final Map<String, List<String>> scopeKeysAndTenants) {

    final Map<String, Map<String, InternalDefinitionVersionWithTenants>> byKeyMap = groupDefinitionsByKeyAndVersion(
      userId, definitions
    );

    List<DefinitionAvailableVersionsWithTenants> definitionAvailableVersionsWithTenants =
      mapToAvailableDefinitionVersionsWithTenants(byKeyMap);

    if (scopeKeysAndTenants != null) {
      // we have to filter as for shared definitions by default all tenants are available in the result
      definitionAvailableVersionsWithTenants = filterDefinitionAvailableVersionsWithTenantsByKeysAndTenants(
        definitionAvailableVersionsWithTenants, scopeKeysAndTenants
      );
    }

    return definitionAvailableVersionsWithTenants;
  }

  private List<DefinitionAvailableVersionsWithTenants> filterDefinitionAvailableVersionsWithTenantsByKeysAndTenants(
    final List<DefinitionAvailableVersionsWithTenants> definitionsWithAvailableVersionsAndTenants,
    final Map<String, List<String>> collectionScope) {
    return definitionsWithAvailableVersionsAndTenants
      .stream()
      .peek(entry -> {
        final List<String> scopeTenantIdsForDefinitionKey = collectionScope.get(entry.getKey());
        entry.setAllTenants(
          entry.getAllTenants().stream()
            .filter(tenantDto -> scopeTenantIdsForDefinitionKey.contains(tenantDto.getId()))
            .collect(Collectors.toList())
        );
        entry.getVersions().forEach(versionEntry -> {
          versionEntry.setTenants(
            versionEntry.getTenants().stream()
              .filter(tenantDto -> scopeTenantIdsForDefinitionKey.contains(tenantDto.getId()))
              .collect(Collectors.toList())
          );
        });
      })
      .collect(Collectors.toList());
  }

  private Map<String, Map<String, InternalDefinitionVersionWithTenants>> groupDefinitionsByKeyAndVersion(
    final String userId,
    final List<? extends DefinitionOptimizeDto> definitions) {

    final Map<String, Map<String, InternalDefinitionVersionWithTenants>> byKeyMap = new HashMap<>();
    final Map<String, Map<String, TenantDto>> authorizedTenantsByEngine = new HashMap<>();
    for (DefinitionOptimizeDto process : definitions) {
      final String definitionKey = process.getKey();
      byKeyMap.putIfAbsent(definitionKey, new HashMap<>());

      final String version = process.getVersion();
      final Map<String, InternalDefinitionVersionWithTenants> byVersionMap = byKeyMap.get(definitionKey);
      byVersionMap.putIfAbsent(
        process.getVersion(),
        new InternalDefinitionVersionWithTenants(
          definitionKey, process.getName(), process.getVersion(), process.getVersionTag(), new HashSet<>()
        )
      );

      final String tenantId = process.getTenantId();
      boolean isTenantSpecificDefinition = tenantId != null;
      final Map<String, TenantDto> authorizedTenants = authorizedTenantsByEngine.computeIfAbsent(
        process.getEngine(), engineAlias -> getAvailableTenantsForUserAndEngine(userId, engineAlias)
      );

      if (isTenantSpecificDefinition) {
        final TenantDto tenantDto = authorizedTenants.get(tenantId);
        if (tenantDto != null) {
          byVersionMap.get(version).getTenants().add(tenantDto);
        }
      } else {
        byVersionMap.get(version).getTenants().addAll(authorizedTenants.values());
      }
    }
    return byKeyMap;
  }

  private Map<String, TenantDto> getAvailableTenantsForUserAndEngine(final String userId,
                                                                     final String engineAlias) {
    return tenantService.getTenantsForUserByEngine(userId, engineAlias)
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, v -> v));
  }

  private List<DefinitionAvailableVersionsWithTenants> mapToAvailableDefinitionVersionsWithTenants(
    final Map<String, Map<String, InternalDefinitionVersionWithTenants>> byKeyMap) {

    return byKeyMap.entrySet().stream()
      .map(byKeyEntry -> {
        final String definitionName = byKeyEntry.getValue().values().iterator().next().getName();
        final Set<TenantDto> allVersionsTenants = new HashSet<>();
        final List<DefinitionVersionWithTenants> versions = byKeyEntry.getValue().values().stream()
          .map(internalDto -> new DefinitionVersionWithTenants(
            internalDto.key,
            internalDto.name,
            internalDto.version,
            internalDto.versionTag,
            new ArrayList<>(internalDto.tenants)
          ))
          .peek(DefinitionVersionWithTenants::sort)
          .peek(definitionWithTenants -> allVersionsTenants.addAll(definitionWithTenants.getTenants()))
          .collect(Collectors.toList());

        final DefinitionAvailableVersionsWithTenants definitionVersionsWithTenants =
          new DefinitionAvailableVersionsWithTenants(
            byKeyEntry.getKey(), definitionName, versions, new ArrayList<>(allVersionsTenants)
          );
        definitionVersionsWithTenants.sort();

        return definitionVersionsWithTenants;
      })
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(Collectors.toList());
  }

  @AllArgsConstructor
  @Data
  private static final class InternalDefinitionVersionWithTenants {
    private String key;
    private String name;
    private String version;
    private String versionTag;
    // internal dto uses a set to eliminate duplicates
    private Set<TenantDto> tenants;
  }
}
