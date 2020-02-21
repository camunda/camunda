/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenants;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;
import org.camunda.optimize.service.es.reader.DefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

@AllArgsConstructor
@Component
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
          filterDefinitionsWithTenantsByAuthorizations(userId, Stream.of(definitionWithTenantIdsDto)).findFirst();
        return authorizedDefinition.orElseThrow(() -> new ForbiddenException(String.format(
          "User [%s] is not authorized to definition with type [%s] and key [%s].", userId, type, key
        )));
      });
  }

  public List<DefinitionWithTenantsDto> getDefinitions(final String userId) {
    final Stream<DefinitionWithTenantIdsDto> stream = definitionReader.getDefinitions().stream();
    return filterDefinitionsWithTenantsByAuthorizations(userId, stream).collect(toList())
      .stream()
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(toList());
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenantForUser(final String userId) {
    return getDefinitionsGroupedByTenant(userId);
  }

  public List<DefinitionAvailableVersionsWithTenants> getDefinitionsGroupedByVersionAndTenantForType(
    final String userId,
    final Map<String, List<String>> definitionKeyAndTenantFilter,
    final DefinitionType definitionType) {

    return getDefinitionsGroupedByVersionAndTenantForType(userId, definitionType)
      .stream()
      .filter(def -> definitionKeyAndTenantFilter.keySet().contains(def.getKey()))
      .peek(def -> {
        final Map<String, TenantDto> userAuthorizedTenants = getAuthorizedTenantDtosForUser(userId);
        Set<String> collectionTenantIds = definitionKeyAndTenantFilter.get(def.getKey())
          .stream()
          .filter(tenantId -> userAuthorizedTenants.keySet().contains(tenantId))
          .collect(toSet());
        List<TenantDto> filteredAllTenants = def.getAllTenants()
          .stream()
          .filter(tenant -> collectionTenantIds.contains(tenant.getId()))
          .collect(toList());
        List<DefinitionVersionWithTenants> filteredVersions = def.getVersions()
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

  public List<DefinitionAvailableVersionsWithTenants> getDefinitionsGroupedByVersionAndTenantForType(
    final String userId,
    final DefinitionType definitionType) {

    final Map<String, DefinitionAvailableVersionsWithTenants> definitionsGroupedByTenantAndVersion =
      definitionReader.getDefinitionsGroupedByVersionAndTenantForType(definitionType);

    final Map<String, TenantDto> authorizedTenantDtosById = getAuthorizedTenantDtosForUser(userId);

    return definitionsGroupedByTenantAndVersion
      .entrySet()
      .stream()
      .map(entry -> filterDefinitionAvailableVersionsWithTenantsByTenantAuthorization(
        entry.getValue(),
        authorizedTenantDtosById,
        userId,
        definitionType
      ))
      .filter(definition -> !definition.getAllTenants().isEmpty())
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(toList());
  }

  public DefinitionAvailableVersionsWithTenants filterDefinitionAvailableVersionsWithTenantsByTenantAuthorization(
    final DefinitionAvailableVersionsWithTenants definitionDto,
    final Map<String, TenantDto> definitionKeyAndTenantFilterMap,
    final String userId,
    final DefinitionType definitionType) {
    List<DefinitionVersionWithTenants> filteredVersions;
    if (isEventProcessDefinition(definitionDto.getKey())) {
      filteredVersions = definitionDto.getVersions()
        .stream()
        .map(defVersionWithTenants -> new DefinitionVersionWithTenants(
          defVersionWithTenants.getKey(),
          defVersionWithTenants.getName(),
          defVersionWithTenants.getVersion(),
          defVersionWithTenants.getVersionTag(),
          Lists.newArrayList(new TenantDto(null, "Not defined", null))
        ))
        .collect(toList());
    } else {
      filteredVersions = definitionDto.getVersions()
        .stream()
        .map(defVersionWithTenants -> {
          final boolean hasNotDefinedTenant = defVersionWithTenants.getTenants().contains(TENANT_NOT_DEFINED);
          List<TenantDto> tenants = hasNotDefinedTenant
            ? Lists.newArrayList(definitionKeyAndTenantFilterMap.values())
            : defVersionWithTenants.getTenants()
            .stream()
            .filter(tenant -> definitionKeyAndTenantFilterMap.keySet().contains(tenant.getId()))
            .filter(tenant -> definitionAuthorizationService.isAuthorizedToSeeDefinition(
              userId,
              IdentityType.USER,
              defVersionWithTenants.getKey(),
              definitionType,
              tenant.getId()
            ))
            .map(tenantDto -> definitionKeyAndTenantFilterMap.get(tenantDto.getId()))
            .collect(toList());
          tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
          return new DefinitionVersionWithTenants(
            defVersionWithTenants.getKey(),
            defVersionWithTenants.getName(),
            defVersionWithTenants.getVersion(),
            defVersionWithTenants.getVersionTag(),
            tenants
          );
        })
        .filter(v -> !v.getTenants().isEmpty())
        .collect(toList());
    }

    return new DefinitionAvailableVersionsWithTenants(
      definitionDto.getKey(),
      definitionDto.getName(),
      filteredVersions,
      filteredVersions.stream()
        .flatMap(v -> v.getTenants().stream())
        .distinct()
        .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
        .collect(toList())
    );
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant(final String userId) {

    final Map<String, TenantIdWithDefinitionsDto> definitionsGroupedByTenant =
      definitionReader.getDefinitionsGroupedByTenant();

    final Map<String, TenantRestDto> authorizedTenantDtosById = getAuthorizedTenantRestDtosForUser(userId);
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
        final TenantRestDto tenantDto = tenantIdWithDefinitionsDtoTenantDtoPair.getRight();
        // now filter for tenant and definition key pair authorization
        final List<SimpleDefinitionDto> authorizedDefinitions = tenantIdWithDefinitionsDto.getDefinitions().stream()
          .filter(definition -> definition.getIsEventProcess()
            || definitionAuthorizationService.isAuthorizedToSeeDefinition(
            userId, IdentityType.USER, definition.getKey(), definition.getType(), tenantIdWithDefinitionsDto.getId()
          ))
          // sort by name case insensitive
          .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
          .collect(toList());
        return new TenantWithDefinitionsDto(tenantDto.getId(), tenantDto.getName(), authorizedDefinitions);
      })
      .filter(tenantWithDefinitionsDto -> tenantWithDefinitionsDto.getDefinitions().size() > 0)
      .sorted(Comparator.comparing(TenantWithDefinitionsDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(toList());
  }

  public Boolean isEventProcessDefinition(final String key) {
    Optional<DefinitionWithTenantIdsDto> definitionOpt = definitionReader.getDefinition(PROCESS, key);
    return definitionOpt.map(SimpleDefinitionDto::getIsEventProcess).orElse(false);
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

  private Stream<DefinitionWithTenantsDto> filterDefinitionsWithTenantsByAuthorizations(final String userId,
                                                                                        final Stream<DefinitionWithTenantIdsDto> stream) {
    // load all authorized tenants at once to speedup mapping
    final Map<String, TenantRestDto> authorizedTenantDtosById = getAuthorizedTenantRestDtosForUser(userId);

    return stream
      .peek(definitionWithTenantIdsDto -> {
        if (!definitionWithTenantIdsDto.getIsEventProcess()) { // We do not support tenants for EventProcessDefinition
          // we want all tenants to be available for shared definitions,
          // as technically there can be data for any of them
          final boolean hasNotDefinedTenant = definitionWithTenantIdsDto.getTenantIds()
            .contains(TENANT_NOT_DEFINED.getId());
          if (hasNotDefinedTenant) {
            definitionWithTenantIdsDto.setTenantIds(
              mergeTwoCollectionsWithDistinctValues(
                authorizedTenantDtosById.keySet(), definitionWithTenantIdsDto.getTenantIds()
              )
            );
          }
        }
      })
      .map(definitionWithTenantIds -> {
        // ensure that the user is authorized for the particular definition and tenant combination
        final List<TenantRestDto> authorizedTenants = definitionWithTenantIds.getIsEventProcess()
          ? Lists.newArrayList(new TenantRestDto(TENANT_NOT_DEFINED.getId(), TENANT_NOT_DEFINED.getName()))
          : definitionAuthorizationService
          .filterAuthorizedTenantsForDefinition(
            userId,
            IdentityType.USER,
            definitionWithTenantIds.getKey(),
            definitionWithTenantIds.getType(),
            definitionWithTenantIds.getTenantIds()
          )
          .stream()
          // resolve tenantDto for authorized tenantId
          .map(authorizedTenantDtosById::get)
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing(TenantRestDto::getId, Comparator.nullsFirst(naturalOrder())))
          .collect(toList());

        return new DefinitionWithTenantsDto(
          definitionWithTenantIds.getKey(),
          definitionWithTenantIds.getName(),
          definitionWithTenantIds.getType(),
          definitionWithTenantIds.getIsEventProcess(),
          authorizedTenants
        );
      })
      .filter(definitionWithTenantsDto ->
                definitionWithTenantsDto.getIsEventProcess()
                  || definitionWithTenantsDto.getTenants().size() > 0);
  }

  private Map<String, TenantRestDto> getAuthorizedTenantRestDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(
        TenantDto::getId,
        tenantDto -> new TenantRestDto(tenantDto.getId(), tenantDto.getName())
      ));
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(
        TenantDto::getId,
        Function.identity()
      ));
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(final Collection<T> firstCollection,
                                                                   final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream())
      .distinct()
      .collect(toList());
  }
}
