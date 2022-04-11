/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.util.definition;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import lombok.Value;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.DefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.AbstractCachingAuthorizationService;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.EngineAuthorizations;
import org.camunda.optimize.service.security.ResolvedResourceTypeAuthorizations;
import org.camunda.optimize.service.security.util.tenant.DataSourceTenantAuthorizationService;
import org.camunda.optimize.service.util.configuration.CacheConfiguration;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;

@Conditional(CamundaPlatformCondition.class)
@Component
public class EngineDefinitionAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, EngineAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = List.of(ALL_PERMISSION, READ_HISTORY_PERMISSION);
  private static final List<String> SINGLETON_LIST_NOT_DEFINED_TENANT =
    Collections.singletonList(TENANT_NOT_DEFINED.getId());

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final DataSourceTenantAuthorizationService tenantAuthorizationService;
  private final TenantService tenantService;
  private final LoadingCache<DefinitionTypeAndKey, Set<String>> definitionEnginesReadCache;

  public EngineDefinitionAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                              final DataSourceTenantAuthorizationService tenantAuthorizationService,
                                              final EngineContextFactory engineContextFactory,
                                              final ConfigurationService configurationService,
                                              final DefinitionReader definitionReader,
                                              final TenantService tenantService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.tenantService = tenantService;

    // this cache serves the purpose to reduce the frequency an actual read is triggered
    // as the available engines are not changing often this reduces the latency of calls
    // when multiple authorization checks are done in a short amount of time
    // (mostly listing endpoints for reports and process/decision definitions)
    final CacheConfiguration cacheConfiguration = configurationService.getCaches().getDefinitionEngines();
    this.definitionEnginesReadCache = Caffeine.newBuilder()
      .maximumSize(cacheConfiguration.getMaxSize())
      .expireAfterWrite(cacheConfiguration.getDefaultTtlMillis(), TimeUnit.MILLISECONDS)
      .build(typeAndKey -> definitionReader.getDefinitionEngines(typeAndKey.getType(), typeAndKey.getKey()));
  }

  @Override
  protected Map<String, EngineAuthorizations> fetchAuthorizationsForUserId(final String userId) {
    final List<String> authorizedEngines = applicationAuthorizationService.getAuthorizedEnginesForUser(userId);
    final Map<String, EngineAuthorizations> result = new HashMap<>();
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        if (authorizedEngines.contains(engineContext.getEngineAlias())) {
          result.put(engineContext.getEngineAlias(), fetchEngineAuthorizationsForUser(userId, engineContext));
        } else {
          result.put(engineContext.getEngineAlias(), new EngineAuthorizations(engineContext.getEngineAlias()));
        }
      });
    return result;
  }


  @Override
  protected Map<String, EngineAuthorizations> fetchAuthorizationsForGroupId(final String groupId) {
    final List<String> authorizedEngines = applicationAuthorizationService.getAuthorizedEnginesForGroup(groupId);
    final Map<String, EngineAuthorizations> result = new HashMap<>();
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        if (authorizedEngines.contains(engineContext.getEngineAlias())) {
          result.put(engineContext.getEngineAlias(), fetchEngineAuthorizationsForGroup(groupId, engineContext));
        } else {
          result.put(engineContext.getEngineAlias(), new EngineAuthorizations(engineContext.getEngineAlias()));
        }
      });
    return result;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    super.reloadConfiguration(context);
    definitionEnginesReadCache.invalidateAll();
  }

  public boolean isAuthorizedToSeeDefinition(final String identityId,
                                             final IdentityType identityType,
                                             final String definitionKey,
                                             final DefinitionType definitionType,
                                             final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(
      identityId,
      identityType,
      definitionKey,
      definitionType,
      tenantIds,
      getDefinitionEngines(definitionKey, definitionType)
    );
  }

  public boolean isAuthorizedToSeeDefinition(final String identityId,
                                             final IdentityType identityType,
                                             final String definitionKey,
                                             final DefinitionType definitionType,
                                             final String tenantId,
                                             final Set<String> engines) {
    return isAuthorizedToSeeDefinition(
      identityId, identityType, definitionKey, definitionType, Collections.singletonList(tenantId), engines
    );
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String identityId,
                                                    final IdentityType identityType,
                                                    final String definitionKey,
                                                    final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(
      identityId,
      identityType,
      definitionKey,
      DefinitionType.PROCESS,
      tenantIds
    );
  }

  public boolean isUserAuthorizedToSeeProcessDefinition(final String userId,
                                                        final String processDefinitionKey,
                                                        final String tenantId,
                                                        final String engineAlias) {
    return isAuthorizedToSeeFullyQualifiedDefinition(
      userId, IdentityType.USER, processDefinitionKey, DefinitionType.PROCESS, tenantId, engineAlias
    );
  }

  public boolean isUserAuthorizedToSeeDecisionDefinition(final String identityId,
                                                         final String decisionDefinitionKey,
                                                         final String tenantId,
                                                         final String engineAlias) {
    return isAuthorizedToSeeFullyQualifiedDefinition(
      identityId, IdentityType.USER, decisionDefinitionKey, DefinitionType.DECISION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String identityId,
                                                     final IdentityType identityType,
                                                     final String definitionKey,
                                                     final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(identityId, identityType, definitionKey, DefinitionType.DECISION, tenantIds);
  }

  public List<String> filterAuthorizedTenantsForDefinition(final String identityId,
                                                           final IdentityType identityType,
                                                           final String definitionKey,
                                                           final DefinitionType definitionType,
                                                           final List<String> tenantIds,
                                                           final Set<String> engines) {
    final Map<String, Set<String>> tenantsPerEngine = resolveTenantAndEnginePairs(tenantIds)
      .stream()
      // if a list of engines is provided only consider them, if not any engine can grant access
      .filter(tenantAndEnginePair -> engines.isEmpty() || engines.contains(tenantAndEnginePair.getEngine()))
      .collect(groupingBy(
        TenantAndEnginePair::getEngine,
        Collectors.mapping(TenantAndEnginePair::getTenantId, Collectors.toSet())
      ));

    final Set<String> authorizedTenants = new HashSet<>();
    for (Map.Entry<String, Set<String>> engineAndTenants : tenantsPerEngine.entrySet()) {
      final String engineId = engineAndTenants.getKey();
      for (String tenantId : engineAndTenants.getValue()) {
        if (isAuthorizedToSeeFullyQualifiedDefinition(
          identityId, identityType, definitionKey, definitionType, tenantId, engineId
        )) {
          authorizedTenants.add(tenantId);
        }
      }
      if (authorizedTenants.containsAll(tenantIds)) {
        break;
      }
    }
    return new ArrayList<>(authorizedTenants);
  }

  private boolean isAuthorizedToSeeDefinition(final String identityId,
                                              final IdentityType identityType,
                                              final String definitionKey,
                                              final DefinitionType definitionType,
                                              final List<String> tenantIds,
                                              final Set<String> engines) {
    if (definitionKey == null || definitionKey.isEmpty() || definitionType == null) {
      // null key or type provided is considered authorized
      return true;
    }

    // empty tenant list or null should be replaced with the actual not defined tenant singleton list
    // as a lacking tenant entry defaults to the not defined tenant
    final List<String> nullSafeTenants = Optional.ofNullable(tenantIds).orElse(new ArrayList<>());
    final List<String> expectedTenants = nullSafeTenants.isEmpty() ? SINGLETON_LIST_NOT_DEFINED_TENANT : tenantIds;
    // user needs to be authorized for all considered tenants to get access
    return filterAuthorizedTenantsForDefinition(
      identityId, identityType, definitionKey, definitionType, expectedTenants, engines
    ).size() == expectedTenants.size();
  }

  private Set<String> getDefinitionEngines(final String definitionKey, final DefinitionType definitionType) {
    return definitionEnginesReadCache.get(new DefinitionTypeAndKey(definitionType, definitionKey));
  }

  private int mapToResourceType(final DefinitionType definitionType) {
    switch (definitionType) {
      case PROCESS:
        return RESOURCE_TYPE_PROCESS_DEFINITION;
      case DECISION:
        return RESOURCE_TYPE_DECISION_DEFINITION;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type: " + definitionType);
    }
  }

  private boolean isAuthorizedToSeeFullyQualifiedDefinition(final String identityId,
                                                            final IdentityType identityType,
                                                            final String definitionKey,
                                                            final DefinitionType definitionType,
                                                            final String tenantId,
                                                            final String engineAlias) {
    if (!tenantAuthorizationService.isAuthorizedToSeeTenant(identityId, identityType, tenantId, engineAlias)) {
      return false;
    }

    final Map<String, EngineAuthorizations> authorizationsByEngine = getCachedAuthorizationsForId(
      identityId,
      identityType
    );

    if (authorizationsByEngine == null) {
      return false;
    }

    final ResolvedResourceTypeAuthorizations resourceAuthorizations =
      Optional.ofNullable(authorizationsByEngine.get(engineAlias))
        .map(groupedEngineAuthorizations -> resolveResourceAuthorizations(
          groupedEngineAuthorizations, RELEVANT_PERMISSIONS, mapToResourceType(definitionType)
        ))
        .filter(resolvedAuthorizations -> resolvedAuthorizations.isAuthorizedToAccessResource(definitionKey))
        .orElseGet(ResolvedResourceTypeAuthorizations::new);

    return resourceAuthorizations.isAuthorizedToAccessResource(definitionKey);
  }

  private static EngineAuthorizations fetchEngineAuthorizationsForUser(final String userId,
                                                                       final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizationsForUser = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizationsForUser(userId))
      .addAll(engineContext.getAllDecisionDefinitionAuthorizationsForUser(userId))
      .build();
    return mapToEngineAuthorizations(engineContext.getEngineAlias(), allAuthorizationsForUser);
  }

  private static EngineAuthorizations fetchEngineAuthorizationsForGroup(final String groupId,
                                                                        final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizations = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizations())
      .addAll(engineContext.getAllDecisionDefinitionAuthorizations())
      .build();
    return mapToEngineAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      Collections.singletonList(groupId)
    );
  }

  private List<TenantAndEnginePair> resolveTenantAndEnginePairs(final List<String> tenantIds) {
    // for each tenant resolve the engines they belong to
    return engineContextFactory.getConfiguredEngines()
      .stream()
      .flatMap(engineContext -> tenantService
        .getTenantsByEngine(engineContext.getEngineAlias()).stream()
        .filter(tenantDto -> tenantIds.contains(tenantDto.getId()))
        .map(tenantDto -> new TenantAndEnginePair(tenantDto.getId(), engineContext.getEngineAlias()))
      )
      .collect(toList());
  }

  @Value
  private static class TenantAndEnginePair {
    String tenantId;
    String engine;
  }

  @Value
  private static class DefinitionTypeAndKey {
    DefinitionType type;
    String key;
  }

}
