/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;

@Component
public class TenantAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, ResolvedResourceTypeAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_PERMISSION);

  private final ApplicationAuthorizationService applicationAuthorizationService;

  private Map<String, String> defaultTenantIdByEngine;

  public TenantAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                    final EngineContextFactory engineContextFactory,
                                    final ConfigurationService configurationService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
  }

  public boolean isAuthorizedToSeeAllTenants(final String identityId,
                                             final IdentityType identityType,
                                             final List<String> tenantIds) {
    return isAuthorizedToSeeAllTenants(identityId, identityType, tenantIds, null);
  }

  public boolean isAuthorizedToSeeAllTenants(final String identityId,
                                             final IdentityType identityType,
                                             final List<String> tenantIds,
                                             final String engineAlias) {
    boolean isAuthorized = true;
    for (String tenantId : tenantIds) {
      isAuthorized = isAuthorizedToSeeTenant(identityId, identityType, tenantId, engineAlias);
      if (!isAuthorized) {
        break;
      }
    }
    return isAuthorized;
  }

  public boolean isAuthorizedToSeeTenant(final String identityId,
                                         final IdentityType identityType,
                                         final String tenantId) {
    return isAuthorizedToSeeTenant(identityId, identityType, tenantId, null);
  }

  public boolean isAuthorizedToSeeTenant(final String identityId,
                                         final IdentityType identityType,
                                         final String tenantId,
                                         final String engineAlias) {
    if (tenantId == null || tenantId.isEmpty()) {
      return true;
    } else {
      final Stream<ResolvedResourceTypeAuthorizations> relevantEngineAuthorizations = Optional
        .ofNullable(getCachedAuthorizationsForId(identityId, identityType))
        .map(authorizationsByEngine -> Optional.ofNullable(engineAlias)
          .flatMap(alias -> Optional.ofNullable(authorizationsByEngine.get(alias)))
          .map(Stream::of)
          .orElseGet(() -> authorizationsByEngine.values().stream())
        )
        .orElseGet(Stream::empty);

      return relevantEngineAuthorizations
        .map(engineAuthorizations -> engineAuthorizations.isAuthorizedToAccessResource(tenantId))
        .filter(Boolean::booleanValue)
        .findFirst()
        .orElse(false);
    }
  }

  @Override
  protected void initState() {
    super.initState();
    initDefaultTenantIds();
  }

  @Override
  protected Map<String, ResolvedResourceTypeAuthorizations> fetchAuthorizationsForUserId(final String userId) {
    final Map<String, ResolvedResourceTypeAuthorizations> result = new HashMap<>();
    applicationAuthorizationService.getAuthorizedEnginesForUser(userId)
      .forEach(engineAlias -> {
        final EngineContext engineContext = engineContextFactory.getConfiguredEngineByAlias(engineAlias);
        result.put(engineAlias, resolveUserAuthorizations(userId, engineContext));
      });
    return result;
  }

  @Override
  protected Map<String, ResolvedResourceTypeAuthorizations> fetchAuthorizationsForGroupId(final String groupId) {
    final Map<String, ResolvedResourceTypeAuthorizations> result = new HashMap<>();
    applicationAuthorizationService.getAuthorizedEnginesForGroup(groupId)
      .forEach(engineAlias -> {
        final EngineContext engineContext = engineContextFactory.getConfiguredEngineByAlias(engineAlias);
        result.put(engineAlias, resolveGroupAuthorizations(groupId, engineContext));
      });
    return result;
  }

  private ResolvedResourceTypeAuthorizations resolveUserAuthorizations(String username,
                                                                       EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = engineContext.getAllTenantAuthorizations();
    addEnginesDefaultTenantAuthorizationForUser(username, engineContext, allAuthorizations);

    return resolveResourceAuthorizations(
      engineContext.getEngineAlias(), allAuthorizations, RELEVANT_PERMISSIONS, username, groups, RESOURCE_TYPE_TENANT
    );
  }

  private ResolvedResourceTypeAuthorizations resolveGroupAuthorizations(String groupId,
                                                                        EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getGroupsById(Arrays.asList(groupId));
    final List<AuthorizationDto> allAuthorizations = engineContext.getAllTenantAuthorizations();
    addEnginesDefaultTenantAuthorizationForGroup(groupId, engineContext, allAuthorizations);

    return resolveResourceAuthorizations(
      engineContext.getEngineAlias(), allAuthorizations, RELEVANT_PERMISSIONS, groups, RESOURCE_TYPE_TENANT
    );
  }

  private void addEnginesDefaultTenantAuthorizationForUser(final String username,
                                                           final EngineContext engineContext,
                                                           final List<AuthorizationDto> allAuthorizations) {
    final String enginesDefaultTenantId = defaultTenantIdByEngine.get(engineContext.getEngineAlias());
    if (enginesDefaultTenantId != null) {
      allAuthorizations.add(new AuthorizationDto(
        enginesDefaultTenantId,
        AUTHORIZATION_TYPE_GRANT,
        RELEVANT_PERMISSIONS,
        username,
        null,
        RESOURCE_TYPE_TENANT,
        enginesDefaultTenantId
      ));
    }
  }

  private void addEnginesDefaultTenantAuthorizationForGroup(final String groupId,
                                                            final EngineContext engineContext,
                                                            final List<AuthorizationDto> allAuthorizations) {
    final String enginesDefaultTenantId = defaultTenantIdByEngine.get(engineContext.getEngineAlias());
    if (enginesDefaultTenantId != null) {
      allAuthorizations.add(new AuthorizationDto(
        enginesDefaultTenantId,
        AUTHORIZATION_TYPE_GRANT,
        RELEVANT_PERMISSIONS,
        null,
        groupId,
        RESOURCE_TYPE_TENANT,
        enginesDefaultTenantId
      ));
    }
  }

  private void initDefaultTenantIds() {
    this.defaultTenantIdByEngine = configurationService.getConfiguredEngines().entrySet().stream()
      .filter(entry -> entry.getValue().getDefaultTenantId().isPresent())
      .collect(ImmutableMap.toImmutableMap(
        Map.Entry::getKey, entry -> entry.getValue().getDefaultTenantId().get()
      ));
  }

}
