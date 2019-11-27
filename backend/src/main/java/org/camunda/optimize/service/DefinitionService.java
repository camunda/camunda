/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
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
    return filterDefinitionsWithTenantsByAuthorizations(userId, stream).collect(Collectors.toList())
      .stream()
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(Collectors.toList());
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenantForUser(final String userId) {
    return getDefinitionsGroupedByTenant(userId);
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant(final String userId) {

    final Map<String, TenantIdWithDefinitionsDto> definitionsGroupedByTenant =
      definitionReader.getDefinitionsGroupedByTenant();

    final Map<String, TenantRestDto> authorizedTenantDtosById = getAuthorizedTenantsForUser(userId);
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
          .filter(definition -> definitionAuthorizationService.isAuthorizedToSeeDefinition(
            userId, IdentityType.USER, definition.getKey(), definition.getType(), tenantIdWithDefinitionsDto.getId()
          ))
          // sort by name case insensitive
          .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
          .collect(Collectors.toList());
        return new TenantWithDefinitionsDto(tenantDto.getId(), tenantDto.getName(), authorizedDefinitions);
      })
      .filter(tenantWithDefinitionsDto -> tenantWithDefinitionsDto.getDefinitions().size() > 0)
      .sorted(Comparator.comparing(TenantWithDefinitionsDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(Collectors.toList());
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
    final Map<String, TenantRestDto> authorizedTenantDtosById = getAuthorizedTenantsForUser(userId);

    return stream
      .peek(definitionWithTenantIdsDto -> {
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
      })
      .map(definitionWithTenantIds -> {
        // ensure that the user is authorized for the particular definition and tenant combination
        final List<TenantRestDto> authorizedTenants = definitionAuthorizationService
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
          .collect(Collectors.toList());

        return new DefinitionWithTenantsDto(
          definitionWithTenantIds.getKey(),
          definitionWithTenantIds.getName(),
          definitionWithTenantIds.getType(),
          authorizedTenants
        );
      })
      .filter(definitionWithTenantsDto -> definitionWithTenantsDto.getTenants().size() > 0);
  }

  private Map<String, TenantRestDto> getAuthorizedTenantsForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(Collectors.toMap(
        TenantDto::getId,
        tenantDto -> new TenantRestDto(tenantDto.getId(), tenantDto.getName())
      ));
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(final Collection<T> firstCollection,
                                                                   final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream())
      .distinct()
      .collect(Collectors.toList());
  }
}
