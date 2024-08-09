/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static io.camunda.optimize.service.tenant.CamundaPlatformTenantService.TENANT_NOT_DEFINED;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAllOrLatest;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toMap;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Sets;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantIdWithDefinitionsDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.BpmnModelUtil;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DefinitionService implements ConfigurationReloadable {

  private final DefinitionReader definitionReader;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final TenantService tenantService;

  private final LoadingCache<String, Map<String, DefinitionOptimizeResponseDto>>
      latestProcessDefinitionCache;
  private final LoadingCache<String, Map<String, DefinitionOptimizeResponseDto>>
      latestDecisionDefinitionCache;

  public DefinitionService(
      final DefinitionReader definitionReader,
      final DataSourceDefinitionAuthorizationService definitionAuthorizationService,
      final TenantService tenantService,
      final ConfigurationService configurationService) {
    this.definitionReader = definitionReader;
    this.definitionAuthorizationService = definitionAuthorizationService;
    this.tenantService = tenantService;

    final CacheConfiguration definitionCacheConfiguration =
        configurationService.getCaches().getDefinitions();
    latestProcessDefinitionCache =
        Caffeine.newBuilder()
            .maximumSize(definitionCacheConfiguration.getMaxSize())
            .expireAfterWrite(
                definitionCacheConfiguration.getDefaultTtlMillis(), TimeUnit.MILLISECONDS)
            .build(this::fetchLatestProcessDefinition);
    latestDecisionDefinitionCache =
        Caffeine.newBuilder()
            .maximumSize(definitionCacheConfiguration.getMaxSize())
            .expireAfterWrite(
                definitionCacheConfiguration.getDefaultTtlMillis(), TimeUnit.MILLISECONDS)
            .build(this::fetchLatestDecisionDefinition);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    latestProcessDefinitionCache.invalidateAll();
    latestDecisionDefinitionCache.invalidateAll();
  }

  public String getLatestVersionToKey(final DefinitionType type, final String key) {
    return definitionReader.getLatestVersionToKey(type, key);
  }

  public Optional<DefinitionResponseDto> getDefinitionWithAvailableTenants(
      final DefinitionType type, final String key, final String userId) {
    return definitionReader
        .getDefinitionWithAvailableTenants(type, key)
        .map(
            definitionWithTenantIdsDto -> {
              final Optional<DefinitionResponseDto> authorizedDefinition =
                  filterAndMapDefinitionsWithTenantIdsByAuthorizations(
                          userId, Collections.singleton(definitionWithTenantIdsDto))
                      .findFirst();
              return authorizedDefinition.orElseThrow(
                  () ->
                      new ForbiddenException(
                          String.format(
                              "User [%s] is not authorized to definition with type [%s] and key [%s].",
                              userId, type, key)));
            });
  }

  public List<DefinitionWithTenantIdsDto> getAllDefinitionsWithTenants(final DefinitionType type) {
    return definitionReader.getFullyImportedDefinitionsWithTenantIds(
        type, Collections.emptySet(), Collections.emptySet());
  }

  public Optional<DefinitionWithTenantIdsDto> getProcessDefinitionWithTenants(
      final String processDefinitionKey) {
    return definitionReader.getDefinitionWithAvailableTenants(
        DefinitionType.PROCESS, processDefinitionKey);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type, final String key, final String userId) {
    return getDefinitionVersions(type, key, userId, null);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type,
      final String key,
      final String userId,
      final List<String> tenantIds) {
    final List<DefinitionVersionResponseDto> definitionVersions = new ArrayList<>();

    final Optional<DefinitionResponseDto> optionalDefinition =
        getDefinitionWithAvailableTenants(type, key, userId);
    if (optionalDefinition.isPresent()) {
      final List<String> availableTenants =
          optionalDefinition.get().getTenants().stream().map(TenantDto::getId).toList();
      final Set<String> tenantsToFilterFor =
          CollectionUtils.isEmpty(tenantIds)
              ? Sets.newHashSet(availableTenants)
              : availableTenants.stream().filter(tenantIds::contains).collect(Collectors.toSet());
      tenantsToFilterFor.add(null); // always include shared tenant, even when not in tenantIds
      definitionVersions.addAll(
          definitionReader.getDefinitionVersions(type, key, tenantsToFilterFor));
    }

    return definitionVersions;
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      final DefinitionType type, final String key, final List<String> tenantIds) {
    final Set<String> tenantsToFilterFor = Sets.newHashSet(tenantIds);
    tenantsToFilterFor.add(null);
    return definitionReader.getDefinitionVersions(type, key, tenantsToFilterFor);
  }

  public List<TenantDto> getDefinitionTenants(
      final DefinitionType type,
      final String key,
      final String userId,
      final List<String> versions) {
    return getDefinitionTenants(
        type, key, userId, versions, () -> getLatestVersionToKey(type, key));
  }

  public List<TenantDto> getDefinitionTenants(
      final DefinitionType type,
      final String key,
      final String userId,
      final List<String> versions,
      final Supplier<String> latestVersionSupplier) {
    return definitionReader
        .getDefinitionWithAvailableTenants(type, key, versions, latestVersionSupplier)
        .map(
            definitionWithTenantIdsDto -> {
              final List<TenantDto> authorizedTenants =
                  definitionAuthorizationService.resolveAuthorizedTenantsForProcess(
                      userId,
                      definitionWithTenantIdsDto,
                      definitionWithTenantIdsDto.getTenantIds(),
                      definitionWithTenantIdsDto.getEngines());
              if (authorizedTenants.isEmpty()) {
                throw new ForbiddenException(
                    String.format(
                        "User [%s] is either not authorized to the definition with type [%s] and key [%s]"
                            + " or is not authorized to access any of the tenants this definition  belongs to",
                        userId, type, key));
              }
              return authorizedTenants;
            })
        .orElse(List.of());
  }

  public List<DefinitionResponseDto> getFullyImportedDefinitions(@NonNull final String userId) {
    return getFullyImportedDefinitions(null, null, null, userId);
  }

  public List<DefinitionResponseDto> getFullyImportedDefinitions(
      final DefinitionType definitionType, @NonNull final String userId) {
    return getFullyImportedDefinitions(definitionType, null, null, userId);
  }

  public List<DefinitionResponseDto> getFullyImportedDefinitions(
      final DefinitionType definitionType,
      final Set<String> keys,
      final List<String> tenantIds,
      @NonNull final String userId) {
    final Set<String> tenantsToFilterFor = resolveTenantsToFilterFor(tenantIds, userId);
    final List<DefinitionWithTenantIdsDto> fullyImportedDefinitions =
        definitionReader.getFullyImportedDefinitionsWithTenantIds(
            definitionType, keys, tenantsToFilterFor);
    return filterAndMapDefinitionsWithTenantIdsByAuthorizations(userId, fullyImportedDefinitions)
        // sort by name case-insensitive
        .sorted(
            Comparator.comparing(
                a ->
                    a.getName() == null
                        ? a.getKey().toLowerCase(Locale.ENGLISH)
                        : a.getName().toLowerCase(Locale.ENGLISH)))
        .toList();
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinition(
      final DefinitionType type,
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds) {
    return isDefinitionVersionSetToAllOrLatest(definitionVersions)
        ? (Optional<T>) getLatestCachedDefinition(type, definitionKey, tenantIds)
        : definitionReader.getFirstFullyImportedDefinitionFromTenantsIfAvailable(
            type,
            definitionKey,
            definitionVersions,
            prepareTenantListForDefinitionSearch(tenantIds));
  }

  public <T extends DefinitionOptimizeResponseDto> List<T> getFullyImportedDefinitions(
      final DefinitionType type, final String userId, final boolean withXml) {
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
    addSharedDefinitionsToAllAuthorizedTenantEntries(
        definitionsGroupedByTenant, authorizedTenantDtosById.keySet());

    return definitionsGroupedByTenant.values().stream()
        .map(
            tenantIdWithDefinitionsDto ->
                Pair.of(
                    tenantIdWithDefinitionsDto,
                    authorizedTenantDtosById.get(tenantIdWithDefinitionsDto.getId())))
        // only consider authorized tenants
        .filter(
            tenantIdWithDefinitionsDtoTenantDtoPair ->
                tenantIdWithDefinitionsDtoTenantDtoPair.getRight() != null)
        .map(
            tenantIdWithDefinitionsDtoTenantDtoPair -> {
              final TenantIdWithDefinitionsDto tenantIdWithDefinitionsDto =
                  tenantIdWithDefinitionsDtoTenantDtoPair.getLeft();
              final TenantDto tenantDto = tenantIdWithDefinitionsDtoTenantDtoPair.getRight();
              final String tenantId = tenantIdWithDefinitionsDto.getId();
              // now filter for tenant and definition key pair authorization
              final List<SimpleDefinitionDto> authorizedDefinitions =
                  tenantIdWithDefinitionsDto.getDefinitions().stream()
                      .filter(
                          definition ->
                              definitionAuthorizationService.isAuthorizedToAccessDefinition(
                                  userId, tenantId, definition))
                      // sort by name case-insensitive
                      .sorted(
                          Comparator.comparing(
                              a ->
                                  a.getName() == null
                                      ? a.getKey().toLowerCase(Locale.ENGLISH)
                                      : a.getName().toLowerCase(Locale.ENGLISH)))
                      .toList();
              return new TenantWithDefinitionsResponseDto(
                  tenantDto.getId(), tenantDto.getName(), authorizedDefinitions);
            })
        .filter(tenantWithDefinitionsDto -> !tenantWithDefinitionsDto.getDefinitions().isEmpty())
        .sorted(
            Comparator.comparing(
                TenantWithDefinitionsResponseDto::getId, Comparator.nullsFirst(naturalOrder())))
        .toList();
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXml(
      final DefinitionType type,
      final String userId,
      final String definitionKey,
      final String version,
      final String tenantId) {
    return getDefinitionWithXml(
        type,
        userId,
        definitionKey,
        Collections.singletonList(version),
        Collections.singletonList(tenantId));
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXml(
      final DefinitionType type,
      final String userId,
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds) {
    return getDefinitionWithXmlAsService(type, definitionKey, definitionVersions, tenantIds)
        .map(
            definitionOptimizeDto -> {
              if (definitionAuthorizationService.isAuthorizedToAccessDefinition(
                  userId, definitionOptimizeDto)) {
                return (T) definitionOptimizeDto;
              } else {
                throw new ForbiddenException(
                    "Current user is not authorized to access data of the definition with key "
                        + definitionKey);
              }
            });
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getProcessDefinitionWithXmlAsService(
      final DefinitionType type,
      final String definitionKey,
      final String definitionVersion,
      final String tenantId) {
    return getDefinitionWithXmlAsService(
        type,
        definitionKey,
        Collections.singletonList(definitionVersion),
        Optional.ofNullable(tenantId)
            .map(Collections::singletonList)
            .orElse(Collections.emptyList()));
  }

  public <T extends DefinitionOptimizeResponseDto> Optional<T> getDefinitionWithXmlAsService(
      final DefinitionType type,
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds) {
    if (definitionKey == null || definitionVersions == null || definitionVersions.isEmpty()) {
      return Optional.empty();
    }

    return getDefinition(type, definitionKey, definitionVersions, tenantIds);
  }

  public static List<String> prepareTenantListForDefinitionSearch(
      final List<String> selectedTenantIds) {
    // Prepare list of tenants to check for the requested definition:
    // If only one tenant is listed, first look for the definition on that tenant, then on null
    // tenant
    // If > one tenant is in the list, first look on the null tenant, then on other tenants in the
    // sorted list
    final List<String> tenantIdsForDefinitionSearch = new ArrayList<>(selectedTenantIds);
    tenantIdsForDefinitionSearch.add(null);
    return tenantIdsForDefinitionSearch.stream()
        .distinct()
        .sorted(
            selectedTenantIds.size() == 1
                ? Comparator.nullsLast(Comparator.naturalOrder())
                : Comparator.nullsFirst(Comparator.naturalOrder()))
        .toList();
  }

  public Map<String, String> extractFlowNodeIdAndNames(
      final List<ProcessDefinitionOptimizeDto> definitions) {
    return definitions.stream()
        .map(ProcessDefinitionOptimizeDto::getFlowNodeData)
        .map(BpmnModelUtil::extractFlowNodeNames)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        // can't use Collectors.toMap as value can be null, see
        // https://bugs.openjdk.java.net/browse/JDK-8148463
        .collect(
            HashMap::new,
            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
            HashMap::putAll);
  }

  public Map<String, String> extractUserTaskIdAndNames(
      final List<ProcessDefinitionOptimizeDto> definitions) {
    return definitions.stream()
        .map(ProcessDefinitionOptimizeDto::getUserTaskNames)
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        // can't use Collectors.toMap as value can be null, see
        // https://bugs.openjdk.java.net/browse/JDK-8148463
        .collect(
            HashMap::new,
            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
            HashMap::putAll);
  }

  public Map<String, String> fetchDefinitionFlowNodeNamesAndIdsForProcessInstances(
      final List<ProcessInstanceDto> processInstanceDtos) {
    return extractFlowNodeIdAndNames(
        processInstanceDtos.stream()
            .map(
                processInstanceDto ->
                    Pair.of(
                        processInstanceDto.getProcessDefinitionKey(),
                        processInstanceDto.getProcessDefinitionVersion()))
            .distinct()
            .map(
                processDefinition ->
                    getDefinition(
                        DefinitionType.PROCESS,
                        processDefinition.getLeft(),
                        List.of(processDefinition.getRight()),
                        Collections.emptyList()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ProcessDefinitionOptimizeDto.class::cast)
            .collect(Collectors.toList()));
  }

  public Optional<DefinitionOptimizeResponseDto> getLatestCachedDefinitionOnAnyTenant(
      final DefinitionType type, final String definitionKey) {
    final Comparator<Map.Entry<String, DefinitionOptimizeResponseDto>> defVersionComparator;
    try {
      defVersionComparator =
          Comparator.comparingInt(e -> Integer.parseInt(e.getValue().getVersion()));
      return getCachedTenantToLatestDefinitionMap(type, definitionKey).entrySet().stream()
          .sorted(defVersionComparator.reversed())
          .map(Map.Entry::getValue)
          .findFirst();
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException("Error parsing version string for sorting definitions");
    }
  }

  private Map<String, DefinitionOptimizeResponseDto> fetchLatestProcessDefinition(
      final String definitionKey) {
    return fetchLatestDefinition(DefinitionType.PROCESS, definitionKey);
  }

  private Map<String, DefinitionOptimizeResponseDto> fetchLatestDecisionDefinition(
      final String definitionKey) {
    return fetchLatestDefinition(DefinitionType.DECISION, definitionKey);
  }

  private Map<String, DefinitionOptimizeResponseDto> fetchLatestDefinition(
      final DefinitionType type, final String definitionKey) {
    final List<DefinitionOptimizeResponseDto> definitions =
        definitionReader.getLatestFullyImportedDefinitionsFromTenantsIfAvailable(
            type, definitionKey);
    return definitions.stream()
        .collect(toMap(DefinitionOptimizeResponseDto::getTenantId, Function.identity()));
  }

  private Optional<DefinitionOptimizeResponseDto> getLatestCachedDefinition(
      final DefinitionType type, final String definitionKey, final List<String> tenantIds) {
    final Map<String, DefinitionOptimizeResponseDto> tenantToDefinitionMap =
        getCachedTenantToLatestDefinitionMap(type, definitionKey);

    final List<Map.Entry<String, DefinitionOptimizeResponseDto>> sortedFilteredEntries;
    try {
      sortedFilteredEntries =
          tenantToDefinitionMap.entrySet().stream()
              .filter(e -> tenantIds.contains(e.getKey()) || e.getKey() == null)
              .sorted(Comparator.comparing(e -> Integer.parseInt(e.getValue().getVersion())))
              .toList();
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          "Error while parsing versions while trying to sort definitions");
    }

    // If only one or no definition with the latest version is present, return that
    if (sortedFilteredEntries.size() <= 1) {
      return Optional.ofNullable(
          sortedFilteredEntries.isEmpty() ? null : sortedFilteredEntries.get(0).getValue());
    }

    // If there are duplicate latest definitions on multiple tenants, apply tenant logic by sorting
    // tenantIds
    prepareTenantListForDefinitionSearch(tenantIds);
    return sortedFilteredEntries.stream()
        .filter(e -> tenantIds.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  public boolean definitionExists(final DefinitionType type, final String definitionKey) {
    return !getCachedTenantToLatestDefinitionMap(type, definitionKey).isEmpty();
  }

  public Map<String, DefinitionOptimizeResponseDto> getCachedTenantToLatestDefinitionMap(
      final DefinitionType type, final String definitionKey) {
    if (DefinitionType.PROCESS.equals(type)) {
      return latestProcessDefinitionCache.get(definitionKey);
    }
    return latestDecisionDefinitionCache.get(definitionKey);
  }

  private HashSet<String> resolveTenantsToFilterFor(
      final List<String> tenantIds, final @NonNull String userId) {
    return Sets.newHashSet(
        Optional.ofNullable(tenantIds)
            .map(ids -> filterAuthorizedTenants(userId, ids))
            .map(DefinitionService::prepareTenantListForDefinitionSearch)
            // if none provided load just the authorized ones to not fetch more than accessible
            // anyway
            .orElseGet(() -> tenantService.getTenantIdsForUser(userId)));
  }

  private List<String> filterAuthorizedTenants(final String userId, final List<String> tenantIds) {
    return tenantIds.stream()
        .filter(tenantId -> tenantService.isAuthorizedToSeeTenant(userId, tenantId))
        .toList();
  }

  private void addSharedDefinitionsToAllAuthorizedTenantEntries(
      final Map<String, TenantIdWithDefinitionsDto> definitionsGroupedByTenant,
      final Set<String> authorizedTenantIds) {
    final TenantIdWithDefinitionsDto notDefinedTenantEntry =
        definitionsGroupedByTenant.get(TENANT_NOT_DEFINED.getId());
    if (notDefinedTenantEntry != null) {
      authorizedTenantIds.forEach(
          authorizedTenantId ->
              // definitions of the not defined tenant need to be added to all other tenant
              // entries
              // as technically there can be data on shared definitions for any of them
              definitionsGroupedByTenant.compute(
                  authorizedTenantId,
                  (tenantId, tenantIdWithDefinitionsDto) -> {
                    if (tenantIdWithDefinitionsDto == null) {
                      tenantIdWithDefinitionsDto =
                          new TenantIdWithDefinitionsDto(tenantId, new ArrayList<>());
                    }

                    final List<SimpleDefinitionDto> mergedDefinitionList =
                        mergeTwoCollectionsWithDistinctValues(
                            tenantIdWithDefinitionsDto.getDefinitions(),
                            notDefinedTenantEntry.getDefinitions());

                    tenantIdWithDefinitionsDto.setDefinitions(mergedDefinitionList);

                    return tenantIdWithDefinitionsDto;
                  }));
    }
  }

  private Stream<DefinitionResponseDto> filterAndMapDefinitionsWithTenantIdsByAuthorizations(
      final String userId, final Collection<DefinitionWithTenantIdsDto> definitionsWithTenantIds) {
    return definitionsWithTenantIds.stream()
        .map(
            definitionWithTenantIdsDto ->
                DefinitionResponseDto.from(
                    definitionWithTenantIdsDto,
                    definitionAuthorizationService.resolveAuthorizedTenantsForProcess(
                        userId,
                        definitionWithTenantIdsDto,
                        definitionWithTenantIdsDto.getTenantIds(),
                        definitionWithTenantIdsDto.getEngines())))
        .filter(definitionWithTenantsDto -> !definitionWithTenantsDto.getTenants().isEmpty());
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
        .collect(toMap(TenantDto::getId, Function.identity()));
  }

  private <T extends DefinitionOptimizeResponseDto> List<T> filterAuthorizedDefinitions(
      final String userId, final List<T> definitions) {
    return definitions.stream()
        .filter(
            definition ->
                definitionAuthorizationService.isAuthorizedToAccessDefinition(userId, definition))
        .collect(Collectors.toList());
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(
      final Collection<T> firstCollection, final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream()).distinct().toList();
  }
}
