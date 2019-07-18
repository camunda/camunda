/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersions;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Comparator.naturalOrder;

@AllArgsConstructor
abstract class AbstractDefinitionService {
  protected TenantService tenantService;
  protected DefinitionAuthorizationService definitionAuthorizationService;

  List<DefinitionAvailableVersionsWithTenants> createDefinitionsWithAvailableVersionsAndTenants(
    final String userId,
    final List<? extends DefinitionOptimizeDto> definitions) {

    final Map<String, Map<String, InternalDefinitionWithTenants>> byKeyMap = groupDefinitionsByKeyAndVersion(
      userId, definitions
    );

    return mapToAvailableDefinitionVersionsWithTenants(byKeyMap);
  }

  private Map<String, Map<String, InternalDefinitionWithTenants>> groupDefinitionsByKeyAndVersion(
    final String userId,
    final List<? extends DefinitionOptimizeDto> definitions) {

    final Map<String, Map<String, InternalDefinitionWithTenants>> byKeyMap = new HashMap<>();
    for (DefinitionOptimizeDto process : definitions) {
      final String definitionKey = process.getKey();
      byKeyMap.putIfAbsent(definitionKey, new HashMap<>());

      final String version = process.getVersion();
      final Map<String, InternalDefinitionWithTenants> byVersionMap = byKeyMap.get(definitionKey);
      byVersionMap.putIfAbsent(
        process.getVersion(),
        new InternalDefinitionWithTenants(
          definitionKey,
          process.getName(),
          process.getVersion(),
          process.getVersionTag(),
          new HashSet<>()
        )
      );

      final String tenantId = process.getTenantId();
      boolean isTenantSpecificDefinition = tenantId != null;
      final Map<String, TenantDto> tenantsForUserAndDefinitionByKey =
        getAvailableTenantsForUserAndDefinition(userId, process);
      if (isTenantSpecificDefinition) {
        final TenantDto tenantDto = tenantsForUserAndDefinitionByKey.get(tenantId);
        if (tenantDto != null) {
          byVersionMap.get(version).getTenants().add(tenantDto);
        }
      } else {
        byVersionMap.get(version).getTenants().addAll(tenantsForUserAndDefinitionByKey.values());
      }
    }
    return byKeyMap;
  }

  private Map<String, TenantDto> getAvailableTenantsForUserAndDefinition(final String userId,
                                                                         final DefinitionOptimizeDto definition) {
    return tenantService
      .getTenantsForUserByEngine(userId, definition.getEngine())
      .stream()
      .filter(tenantDto -> definitionAuthorizationService.isAuthorizedToSeeDefinition(userId, definition))
      .collect(Collectors.toMap(TenantDto::getId, v -> v));
  }

  private List<DefinitionAvailableVersionsWithTenants> mapToAvailableDefinitionVersionsWithTenants(
    final Map<String, Map<String, InternalDefinitionWithTenants>> byKeyMap) {

    return byKeyMap.entrySet().stream()
      .map(byKeyEntry -> {
        final String definitionName = byKeyEntry.getValue().values().iterator().next().getName();
        final List<DefinitionVersions> versions = byKeyEntry.getValue().values().stream()
          .map(internalDto -> new DefinitionVersions(
            internalDto.key, internalDto.name, internalDto.version, internalDto.versionTag
          ))
          .collect(Collectors.toList());
        final List<TenantDto> tenants = byKeyEntry.getValue().values().stream()
          .map(InternalDefinitionWithTenants::getTenants)
          .flatMap(Collection::stream)
          .collect(Collectors.toSet())
          .stream()
          .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
          .collect(Collectors.toList());

        final DefinitionAvailableVersionsWithTenants definitionVersionsWithTenants =
          new DefinitionAvailableVersionsWithTenants(byKeyEntry.getKey(), definitionName, versions, tenants);
        definitionVersionsWithTenants.sort();

        return definitionVersionsWithTenants;
      })
      // just to ensure consistent ordering
      .sorted(Comparator.comparing(DefinitionAvailableVersionsWithTenants::getKey))
      .collect(Collectors.toList());
  }

  @AllArgsConstructor
  @Data
  private static final class InternalDefinitionWithTenants {
    private String key;
    private String name;
    private String version;
    private String versionTag;
    // internal dto uses a set to eliminate duplicates
    private Set<TenantDto> tenants;
  }
}
