/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenants;

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
  private TenantService tenantService;

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
        new InternalDefinitionWithTenants(definitionKey, process.getName(), process.getVersion(), new HashSet<>())
      );

      final String tenantId = process.getTenantId();
      boolean isTenantSpecificDefinition = tenantId != null;
      final Map<String, TenantDto> allAvailableTenantsById = getTenantsForUserAndEngineGroupedById(userId, process);
      if (isTenantSpecificDefinition) {
        final TenantDto tenantDto = allAvailableTenantsById.get(tenantId);
        if (tenantDto != null) {
          byVersionMap.get(version).getTenants().add(tenantDto);
        }
      } else {
        byVersionMap.get(version).getTenants().addAll(allAvailableTenantsById.values());
      }
    }
    return byKeyMap;
  }

  private Map<String, TenantDto> getTenantsForUserAndEngineGroupedById(final String userId,
                                                                       final DefinitionOptimizeDto process) {
    return tenantService
      .getTenantsForUserByEngine(userId, process.getEngine())
      .stream()
      .collect(Collectors.toMap(TenantDto::getId, v -> v));
  }

  private List<DefinitionAvailableVersionsWithTenants> mapToAvailableDefinitionVersionsWithTenants(
    final Map<String, Map<String, InternalDefinitionWithTenants>> byKeyMap) {

    return byKeyMap.entrySet().stream()
      .map(byKeyEntry -> {
        final String definitionName = byKeyEntry.getValue().values().iterator().next().getName();
        final Set<TenantDto> allVersionsTenants = new HashSet<>();
        final List<DefinitionWithTenants> versions = byKeyEntry.getValue().values().stream()
          .map(internalDto -> new DefinitionWithTenants(
            internalDto.key, internalDto.name, internalDto.version, new ArrayList<>(internalDto.tenants)
          ))
          .peek(DefinitionWithTenants::sort)
          .peek(definitionWithTenants -> allVersionsTenants.addAll(definitionWithTenants.getTenants()))
          .collect(Collectors.toList());

        final DefinitionWithTenants allVersionDefinitionWithTenants = new DefinitionWithTenants(
          byKeyEntry.getKey(), definitionName, ReportConstants.ALL_VERSIONS, new ArrayList<>(allVersionsTenants)
        );
        allVersionDefinitionWithTenants.sort();
        versions.add(allVersionDefinitionWithTenants);

        final DefinitionAvailableVersionsWithTenants definitionVersionsWithTenants =
          new DefinitionAvailableVersionsWithTenants(byKeyEntry.getKey(), definitionName, versions);
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
    // internal dto uses a set to eliminate duplicates
    private Set<TenantDto> tenants;
  }
}
