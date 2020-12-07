/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.DefinitionReader;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

@AllArgsConstructor
@Component
@Slf4j
public class DefinitionService {
  private final DefinitionReader definitionReader;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final TenantService tenantService;
  private final CamundaActivityEventReader camundaActivityEventReader;

  public Optional<DefinitionWithTenantsResponseDto> getDefinitionWithAvailableTenants(final DefinitionType type,
                                                                                      final String key,
                                                                                      final String userId) {
    return definitionReader.getDefinitionWithAvailableTenants(type, key)
      .map(definitionWithTenantIdsDto -> {
        final Optional<DefinitionWithTenantsResponseDto> authorizedDefinition =
          filterAndMapDefinitionsWithTenantIdsByAuthorizations(
            userId,
            Collections.singleton(definitionWithTenantIdsDto)
          ).findFirst();
        return authorizedDefinition.orElseThrow(() -> new ForbiddenException(String.format(
          "User [%s] is not authorized to definition with type [%s] and key [%s].", userId, type, key
        )));
      });
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                                  final String key,
                                                                  final String userId) {
    return getDefinitionVersions(type, key, userId, null);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                                  final String key,
                                                                  final String userId,
                                                                  final List<String> tenantIds) {
    final List<DefinitionVersionResponseDto> definitionVersions = new ArrayList<>();

    final Optional<DefinitionWithTenantsResponseDto> optionalDefinition = getDefinitionWithAvailableTenants(
      type,
      key,
      userId
    );
    if (optionalDefinition.isPresent()) {
      final List<String> availableTenants = optionalDefinition.get().getTenants().stream()
        .map(TenantDto::getId)
        .collect(toList());
      final Set<String> tenantsToFilterFor = resolveTenantsToFilterFor(
        CollectionUtils.isEmpty(tenantIds)
          ? availableTenants
          : availableTenants.stream().filter(tenantIds::contains).collect(toList()),
        userId
      );

      definitionVersions.addAll(definitionReader.getDefinitionVersions(type, key, tenantsToFilterFor));
    }

    return definitionVersions;
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(final DefinitionType type,
                                                                  final String key,
                                                                  final List<String> tenantIds) {
    final List<DefinitionVersionResponseDto> definitionVersions = new ArrayList<>();

    final Optional<DefinitionWithTenantIdsDto> optionalDefinition =
      definitionReader.getDefinitionWithAvailableTenants(type, key);
    if (optionalDefinition.isPresent()) {
      final List<String> availableTenants = optionalDefinition.get().getTenantIds();
      final List<String> tenantsToFilterFor = prepareTenantListForDefinitionSearch(
        CollectionUtils.isEmpty(tenantIds)
          ? availableTenants
          : availableTenants.stream().filter(tenantIds::contains).collect(toList())
      );

      definitionVersions.addAll(definitionReader.getDefinitionVersions(type, key, Sets.newHashSet(tenantsToFilterFor)));
    }

    return definitionVersions;
  }

  public List<TenantDto> getDefinitionTenants(final DefinitionType type,
                                              final String key,
                                              final String userId,
                                              final List<String> versions) {
    return getDefinitionTenants(type, key, userId, versions, () -> definitionReader.getLatestVersionToKey(type, key));
  }

  public List<TenantDto> getDefinitionTenants(final DefinitionType type,
                                              final String key,
                                              final String userId,
                                              final List<String> versions,
                                              final Supplier<String> latestVersionSupplier) {
    final List<String> tenantIdsFromDefinition = definitionReader.getDefinitionTenantIds(
      type, key, versions, latestVersionSupplier
    );
    final Map<String, TenantDto> tenantAvailableToUser = tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(TenantDto::getId, Function.identity()));
    final List<TenantDto> result = new ArrayList<>();
    if (tenantIdsFromDefinition.contains(TENANT_NOT_DEFINED.getId())) {
      // enrich all available tenants if the not defined tenants is among the results
      result.addAll(tenantAvailableToUser.values());
    } else {
      tenantIdsFromDefinition.forEach(tenantId -> {
        final TenantDto authorizedTenant = tenantAvailableToUser.get(tenantId);
        if (authorizedTenant != null) {
          result.add(authorizedTenant);
        } else {
          log.debug(
            "Current user is not authorized to access tenant with id [{}], will not include it in the result.", tenantId
          );
        }
      });
    }

    if (result.isEmpty() ||
      !definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, type, key, result.stream().map(TenantDto::getId).collect(Collectors.toList())
      )
    ) {
      throw new ForbiddenException(String.format(
        "User [%s] is either not authorized to the definition with type [%s] and key [%s] or lacks authorization to " +
          "every tenant this definition belongs to.",
        userId, type, key
      ));
    }

    return result.stream()
      .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(Collectors.toList());
  }

  public List<DefinitionWithTenantsResponseDto> getFullyImportedCamundaEventImportedDefinitions(final String userId) {
    final Set<String> camundaEventImportedKeys = camundaActivityEventReader.getIndexSuffixesForCurrentActivityIndices();
    final List<DefinitionWithTenantsResponseDto> allProcessDefs = getFullyImportedDefinitions(
      DefinitionType.PROCESS,
      userId
    );
    return allProcessDefs.stream()
      .filter(def -> camundaEventImportedKeys.contains(def.getKey().toLowerCase()))
      .collect(toList());
  }

  public List<DefinitionWithTenantsResponseDto> getFullyImportedDefinitions(@NonNull final String userId) {
    return getFullyImportedDefinitions(null, null, null, userId);
  }

  public List<DefinitionWithTenantsResponseDto> getFullyImportedDefinitions(final DefinitionType definitionType,
                                                                            @NonNull final String userId) {
    return getFullyImportedDefinitions(definitionType, null, null, userId);
  }

  public List<DefinitionWithTenantsResponseDto> getFullyImportedDefinitions(final DefinitionType definitionType,
                                                                            final Set<String> keys,
                                                                            final List<String> tenantIds,
                                                                            @NonNull final String userId) {
    final Set<String> tenantsToFilterFor = resolveTenantsToFilterFor(tenantIds, userId);
    final List<DefinitionWithTenantIdsDto> fullyImportedDefinitions = definitionReader
      .getFullyImportedDefinitions(definitionType, keys, tenantsToFilterFor);
    return filterAndMapDefinitionsWithTenantIdsByAuthorizations(userId, fullyImportedDefinitions)
      // sort by name case insensitive
      .sorted(Comparator.comparing(a -> a.getName() == null ? a.getKey().toLowerCase() : a.getName().toLowerCase()))
      .collect(toList());
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getLatestDefinition(final DefinitionType type,
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

  public <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedProcessDefinitions(final DefinitionType type,
                                                                                              final String userId,
                                                                                              final boolean withXml) {
    log.debug("Fetching definitions of type " + type);
    List<T> definitionsResult = definitionReader.getFullyImportedDefinitions(type, withXml);

    if (userId != null) {
      definitionsResult = filterAuthorizedDefinitions(userId, definitionsResult);
    }

    return definitionsResult;
  }

  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant(final String userId) {

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
        return new TenantWithDefinitionsResponseDto(tenantDto.getId(), tenantDto.getName(), authorizedDefinitions);
      })
      .filter(tenantWithDefinitionsDto -> !tenantWithDefinitionsDto.getDefinitions().isEmpty())
      .sorted(Comparator.comparing(TenantWithDefinitionsResponseDto::getId, Comparator.nullsFirst(naturalOrder())))
      .collect(toList());
  }

  public Optional<String> getDefinitionXml(final DefinitionType type,
                                           final String userId,
                                           final String definitionKey,
                                           final List<String> versions) {
    return getDefinitionXml(type, userId, definitionKey, versions, (String) null);
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXml(final DefinitionType type,
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

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXml(final DefinitionType type,
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

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getProcessDefinitionWithXmlAsService(final DefinitionType type,
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

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXmlAsService(final DefinitionType type,
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

  private HashSet<String> resolveTenantsToFilterFor(final List<String> tenantIds, final @NonNull String userId) {
    return Sets.newHashSet(
      Optional.ofNullable(tenantIds)
        .map(ids -> filterAuthorizedTenants(userId, ids))
        .map(DefinitionService::prepareTenantListForDefinitionSearch)
        // if none provided load just the authorized ones to not fetch more than accessible anyway
        .orElseGet(() -> tenantService.getTenantIdsForUser(userId))
    );
  }

  private List<String> filterAuthorizedTenants(final String userId, final List<String> tenantIds) {
    return tenantIds.stream()
      .filter(tenantId -> tenantService.isAuthorizedToSeeTenant(userId, tenantId))
      .collect(toList());
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

  private Stream<DefinitionWithTenantsResponseDto> filterAndMapDefinitionsWithTenantIdsByAuthorizations(
    final String userId,
    final Collection<DefinitionWithTenantIdsDto> definitionsWithTenantIds) {
    return definitionsWithTenantIds
      .stream()
      .map(definitionWithTenantIdsDto -> DefinitionWithTenantsResponseDto.from(
        definitionWithTenantIdsDto,
        definitionAuthorizationService.resolveAuthorizedTenantsForProcess(
          userId,
          definitionWithTenantIdsDto,
          definitionWithTenantIdsDto.getTenantIds(),
          definitionWithTenantIdsDto.getEngines()
        )
      ))
      .filter(definitionWithTenantsDto -> !definitionWithTenantsDto.getTenants().isEmpty());
  }


  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(
        TenantDto::getId,
        Function.identity()
      ));
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> filterAuthorizedDefinitions(final String userId,
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
