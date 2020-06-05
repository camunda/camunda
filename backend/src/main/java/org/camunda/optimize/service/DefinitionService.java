/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.service.es.reader.DefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

@AllArgsConstructor
@Component
@Slf4j
public class DefinitionService {
  private final DefinitionReader definitionReader;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final TenantService tenantService;

  public Optional<DefinitionWithTenantsDto> getDefinition(final DefinitionType type,
                                                          final String key,
                                                          final String userId) {
    return definitionReader.getDefinition(type, key)
      .map(definitionWithTenantIdsDto -> {
        final Optional<DefinitionWithTenantsDto> authorizedDefinition =
          filterAndMapDefinitionsWithTenantIdsByAuthorizations(
            userId,
            Collections.singleton(definitionWithTenantIdsDto)
          ).findFirst();
        return authorizedDefinition.orElseThrow(() -> new ForbiddenException(String.format(
          "User [%s] is not authorized to definition with type [%s] and key [%s].", userId, type, key
        )));
      });
  }

  public List<DefinitionWithTenantsDto> getDefinitions(final String userId) {
    return filterAndMapDefinitionsWithTenantIdsByAuthorizations(userId, definitionReader.getDefinitionsOfAllTypes())
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(toList());
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getLatestDefinition(final DefinitionType type,
                                                                           final String definitionKey,
                                                                           final List<String> definitionVersions,
                                                                           final List<String> tenantIds) {
    return definitionReader.getFirstDefinitionFromTenantsIfAvailable(
      type,
      definitionKey,
      definitionVersions,
      prepareTenantListForDefinitionSearch(tenantIds)
    );
  }

  public <T extends DefinitionOptimizeDto> List<T> getFullyImportedProcessDefinitions(final DefinitionType type,
                                                                                      final String userId,
                                                                                      final boolean withXml) {
    log.debug("Fetching definitions of type " + type);
    List<T> definitionsResult = (List<T>) definitionReader.getFullyImportedDefinitions(type, withXml);

    if (userId != null) {
      definitionsResult = filterAuthorizedDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public List<DefinitionVersionsWithTenantsDto> getDefinitionsGroupedByVersionAndTenantForType(
    final DefinitionType definitionType,
    final boolean excludeEventProcesses,
    final String userId) {

    final List<DefinitionVersionsWithTenantsDto> definitionsWithVersionsAndTenants =
      definitionReader.getDefinitionsWithVersionsAndTenantsForType(definitionType, excludeEventProcesses);

    return definitionsWithVersionsAndTenants
      .stream()
      .map(definitionWithVersionsAndTenants -> filterDefinitionAvailableVersionsWithTenantsByTenantAuthorization(
        definitionWithVersionsAndTenants, userId
      ))
      .filter(definition -> !definition.getAllTenants().isEmpty())
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(toList());
  }

  public List<DefinitionVersionsWithTenantsDto> getDefinitionsGroupedByVersionAndTenantForType(
    final DefinitionType definitionType,
    final boolean excludeEventProcesses,
    final String userId,
    final Map<String, List<String>> definitionKeyAndTenantFilter) {

    return getDefinitionsGroupedByVersionAndTenantForType(definitionType, excludeEventProcesses, userId)
      .stream()
      .filter(def -> definitionKeyAndTenantFilter.containsKey(def.getKey()))
      .peek(def -> {
        final Map<String, TenantDto> userAuthorizedTenants = getAuthorizedTenantDtosForUser(userId);
        Set<String> collectionTenantIds = definitionKeyAndTenantFilter.get(def.getKey())
          .stream()
          .filter(userAuthorizedTenants::containsKey)
          .collect(toSet());
        List<TenantDto> filteredAllTenants = def.getAllTenants()
          .stream()
          .filter(tenant -> collectionTenantIds.contains(tenant.getId()))
          .collect(toList());
        List<DefinitionVersionWithTenantsDto> filteredVersions = def.getVersions()
          .stream()
          .peek(versionWithTenants -> versionWithTenants.getTenants().retainAll(filteredAllTenants))
          .filter(versionWithTenants -> !versionWithTenants.getTenants().isEmpty())
          .collect(toList());

        def.setAllTenants(filteredAllTenants);
        def.setVersions(filteredVersions);
      })
      .filter(def -> !def.getAllTenants().isEmpty())
      .collect(toList());
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant(final String userId) {

    final Map<String, TenantIdWithDefinitionsDto> definitionsGroupedByTenant =
      definitionReader.getDefinitionsGroupedByTenant();

    final Map<String, TenantDto> authorizedTenantDtosById = getAuthorizedTenantDtosForUser(userId);
    addSharedDefinitionsToAllAuthorizedTenantEntries(definitionsGroupedByTenant, authorizedTenantDtosById.keySet());

    return definitionsGroupedByTenant.values().stream()
      .map(tenantIdWithDefinitionsDto -> Pair.of(
        tenantIdWithDefinitionsDto,
        authorizedTenantDtosById.get(tenantIdWithDefinitionsDto.getId())
      ))
      // only consider authorized tenants
      .filter(tenantIdWithDefinitionsDtoTenantDtoPair -> tenantIdWithDefinitionsDtoTenantDtoPair.getRight() != null)
      .map(tenantIdWithDefinitionsDtoTenantDtoPair -> {
        final TenantIdWithDefinitionsDto tenantIdWithDefinitionsDto = tenantIdWithDefinitionsDtoTenantDtoPair.getLeft();
        final TenantDto tenantDto = tenantIdWithDefinitionsDtoTenantDtoPair.getRight();
        final String tenantId = tenantIdWithDefinitionsDto.getId();
        // now filter for tenant and definition key pair authorization
        final List<SimpleDefinitionDto> authorizedDefinitions = tenantIdWithDefinitionsDto.getDefinitions().stream()
          .filter(definition -> definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, tenantId, definition
          ))
          // sort by name case insensitive
          .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
          .collect(toList());
        return new TenantWithDefinitionsDto(tenantDto.getId(), tenantDto.getName(), authorizedDefinitions);
      })
      .filter(tenantWithDefinitionsDto -> !tenantWithDefinitionsDto.getDefinitions().isEmpty())
      .sorted(Comparator.comparing(TenantWithDefinitionsDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(toList());
  }

  public Optional<String> getDefinitionXml(final DefinitionType type,
                                           final String userId,
                                           final String definitionKey,
                                           final List<String> versions) {
    return getDefinitionXml(type, userId, definitionKey, versions, (String) null);
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getDefinition(final DefinitionType type,
                                                                     final String userId,
                                                                     final String definitionKey,
                                                                     final String version,
                                                                     final String tenantId) {
    return getDefinitionWithXml(
      type,
      userId,
      definitionKey,
      Collections.singletonList(version),
      Collections.singletonList(tenantId)
    );
  }

  public Optional<String> getDefinitionXml(final DefinitionType type,
                                           final String userId,
                                           final String definitionKey,
                                           final List<String> definitionVersions,
                                           final List<String> tenantIds) {
    switch (type) {
      case PROCESS:
        return getDefinitionWithXml(type, userId, definitionKey, definitionVersions, tenantIds)
          .map(def -> ((ProcessDefinitionOptimizeDto) def).getBpmn20Xml());
      case DECISION:
        return getDefinitionWithXml(type, userId, definitionKey, definitionVersions, tenantIds)
          .map(def -> ((DecisionDefinitionOptimizeDto) def).getDmn10Xml());
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getDefinitionWithXml(final DefinitionType type,
                                                                            final String userId,
                                                                            final String definitionKey,
                                                                            final List<String> definitionVersions,
                                                                            final List<String> tenantIds) {
    return getDefinitionWithXmlAsService(type, definitionKey, definitionVersions, tenantIds)
      .map(definitionOptimizeDto -> {
        if (definitionAuthorizationService.isAuthorizedToAccessDefinition(userId, definitionOptimizeDto)) {
          return (T) definitionOptimizeDto;
        } else {
          throw new ForbiddenException("Current user is not authorized to access data of the definition with key " + definitionKey);
        }
      });
  }

  public Optional<String> getDefinitionXml(final DefinitionType type,
                                           final String userId,
                                           final String definitionKey,
                                           final List<String> definitionVersions,
                                           final String tenantId) {
    return getDefinitionXml(type, userId, definitionKey, definitionVersions, Collections.singletonList(tenantId));
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getProcessDefinitionWithXmlAsService(final DefinitionType type,
                                                                                            final String definitionKey,
                                                                                            final String definitionVersion,
                                                                                            final String tenantId) {
    return getDefinitionWithXmlAsService(
      type,
      definitionKey,
      Collections.singletonList(definitionVersion),
      Optional.ofNullable(tenantId)
        .map(Collections::singletonList)
        .orElse(Collections.emptyList())
    );
  }

  public <T extends DefinitionOptimizeDto> Optional<T> getDefinitionWithXmlAsService(final DefinitionType type,
                                                                                     final String definitionKey,
                                                                                     final List<String> definitionVersions,
                                                                                     final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    return definitionReader.getFirstDefinitionFromTenantsIfAvailable(
      type,
      definitionKey,
      definitionVersions,
      prepareTenantListForDefinitionSearch(tenantIds)
    );
  }

  public static List<String> prepareTenantListForDefinitionSearch(final List<String> selectedTenantIds) {
    // Prepare list of tenants to check for the requested definition:
    // If only one tenant is listed, first look for the definition on that tenant, then on null tenant
    // If > one tenant is in the list, first look on the null tenant, then on other tenants in the sorted list
    List<String> tenantIdsForDefinitionSearch = new ArrayList<>(selectedTenantIds);
    tenantIdsForDefinitionSearch.add(null);
    return tenantIdsForDefinitionSearch.stream()
      .distinct()
      .sorted(selectedTenantIds.size() == 1
                ? Comparator.nullsLast(Comparator.naturalOrder())
                : Comparator.nullsFirst(Comparator.naturalOrder()))
      .collect(toList());
  }

  private DefinitionVersionsWithTenantsDto filterDefinitionAvailableVersionsWithTenantsByTenantAuthorization(
    final DefinitionVersionsWithTenantsDto definitionDto,
    final String userId) {
    final List<DefinitionVersionWithTenantsDto> filteredVersions = definitionDto.getVersions()
      .stream()
      .peek(definitionVersionWithTenants -> {
        final List<TenantDto> tenantDtos = definitionAuthorizationService.resolveAuthorizedTenantsForProcess(
          userId,
          definitionVersionWithTenants,
          definitionVersionWithTenants.getTenants().stream().map(TenantDto::getId).collect(toList())
        );
        definitionVersionWithTenants.setTenants(tenantDtos);
      })
      .filter(v -> !v.getTenants().isEmpty())
      .collect(toList());
    final List<TenantDto> filteredAllTenants = filteredVersions.stream()
      .flatMap(v -> v.getTenants().stream())
      .distinct()
      .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(toList());

    definitionDto.setVersions(filteredVersions);
    definitionDto.setAllTenants(filteredAllTenants);
    return definitionDto;
  }

  private void addSharedDefinitionsToAllAuthorizedTenantEntries(
    final Map<String, TenantIdWithDefinitionsDto> definitionsGroupedByTenant,
    final Set<String> authorizedTenantIds) {
    final TenantIdWithDefinitionsDto notDefinedTenantEntry = definitionsGroupedByTenant.get(TENANT_NOT_DEFINED.getId());
    if (notDefinedTenantEntry != null) {
      authorizedTenantIds.forEach(authorizedTenantId -> {
        // definitions of the not defined tenant need to be added to all other tenant entries
        // as technically there can be data on shared definitions for any of them
        definitionsGroupedByTenant.compute(authorizedTenantId, (tenantId, tenantIdWithDefinitionsDto) -> {
          if (tenantIdWithDefinitionsDto == null) {
            tenantIdWithDefinitionsDto = new TenantIdWithDefinitionsDto(tenantId, new ArrayList<>());
          }

          final List<SimpleDefinitionDto> mergedDefinitionList = mergeTwoCollectionsWithDistinctValues(
            tenantIdWithDefinitionsDto.getDefinitions(),
            notDefinedTenantEntry.getDefinitions()
          );

          tenantIdWithDefinitionsDto.setDefinitions(mergedDefinitionList);

          return tenantIdWithDefinitionsDto;
        });
      });
    }
  }

  private Stream<DefinitionWithTenantsDto> filterAndMapDefinitionsWithTenantIdsByAuthorizations(
    final String userId,
    final Collection<DefinitionWithTenantIdsDto> definitionsWithTenantIds) {
    return definitionsWithTenantIds
      .stream()
      .map(definitionWithTenantIdsDto -> mapToDefinitionWithTenantsDto(
        definitionWithTenantIdsDto,
        definitionAuthorizationService.resolveAuthorizedTenantsForProcess(
          userId, definitionWithTenantIdsDto, definitionWithTenantIdsDto.getTenantIds()
        )
      ))
      .filter(definitionWithTenantsDto -> !definitionWithTenantsDto.getTenants().isEmpty());
  }

  private DefinitionWithTenantsDto mapToDefinitionWithTenantsDto(
    final DefinitionWithTenantIdsDto definitionWithTenantIdsDto,
    final List<TenantDto> authorizedTenants) {
    return new DefinitionWithTenantsDto(
      definitionWithTenantIdsDto.getKey(),
      definitionWithTenantIdsDto.getName(),
      definitionWithTenantIdsDto.getType(),
      definitionWithTenantIdsDto.getIsEventProcess(),
      authorizedTenants
    );
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(
        TenantDto::getId,
        Function.identity()
      ));
  }

  private <T extends DefinitionOptimizeDto> List<T> filterAuthorizedDefinitions(final String userId,
                                                                                final List<T> definitions) {
    return definitions
      .stream()
      .filter(definition -> definitionAuthorizationService.isAuthorizedToAccessDefinition(userId, definition))
      .collect(Collectors.toList());
  }


  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(final Collection<T> firstCollection,
                                                                   final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream())
      .distinct()
      .collect(toList());
  }
}
