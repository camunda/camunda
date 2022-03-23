/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.util.tenant;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.AbstractCachingAuthorizationService;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.ResolvedResourceTypeAuthorizations;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;

@Conditional(CamundaPlatformCondition.class)
@Component
public class CamundaPlatformTenantAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, ResolvedResourceTypeAuthorizations>>
  implements DataSourceTenantAuthorizationService {

  private static final List<String> RELEVANT_PERMISSIONS = List.of(ALL_PERMISSION, READ_PERMISSION);

  private final ApplicationAuthorizationService applicationAuthorizationService;

  private Map<String, String> defaultTenantIdByEngine;

  public CamundaPlatformTenantAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                                   final EngineContextFactory engineContextFactory,
                                                   final ConfigurationService configurationService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
  }

  @Override
  public boolean isAuthorizedToSeeAllTenants(final String identityId,
                                             final IdentityType identityType,
                                             final List<String> tenantIds) {
    return tenantIds.stream()
      .allMatch(tenantId -> isAuthorizedToSeeTenant(identityId, identityType, tenantId));
  }

  @Override
  public boolean isAuthorizedToSeeTenant(final String identityId,
                                         final IdentityType identityType,
                                         final String tenantId) {
    return isAuthorizedToSeeTenant(identityId, identityType, tenantId, null);
  }

  @Override
  public boolean isAuthorizedToSeeTenant(final String identityId,
                                         final IdentityType identityType,
                                         final String tenantId,
                                         final String dataSourceName) {
    if (tenantId == null || tenantId.isEmpty()) {
      return true;
    } else {
      final Stream<ResolvedResourceTypeAuthorizations> relevantEngineAuthorizations = Optional
        .ofNullable(getCachedAuthorizationsForId(identityId, identityType))
        .map(authorizationsByDataSource -> {
          if (dataSourceName == null) {
            // no specific data source, return all
            return authorizationsByDataSource.values().stream();
          } else {
            return Optional.ofNullable(authorizationsByDataSource.get(dataSourceName)).stream();
          }
        })
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
      .forEach(
        engineAlias -> engineContextFactory.getConfiguredEngineByAlias(engineAlias).ifPresent(
          engineContext -> result.put(engineAlias, resolveUserAuthorizations(userId, engineContext))
        )
      );
    return result;
  }

  @Override
  protected Map<String, ResolvedResourceTypeAuthorizations> fetchAuthorizationsForGroupId(final String groupId) {
    final Map<String, ResolvedResourceTypeAuthorizations> result = new HashMap<>();
    applicationAuthorizationService.getAuthorizedEnginesForGroup(groupId)
      .forEach(
        engineAlias -> engineContextFactory.getConfiguredEngineByAlias(engineAlias).ifPresent(
          engineContext -> result.put(engineAlias, resolveGroupAuthorizations(groupId, engineContext))
        )
      );
    return result;
  }

  private ResolvedResourceTypeAuthorizations resolveUserAuthorizations(final String userId,
                                                                       final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizationsOfUser = engineContext.getAllTenantAuthorizationsForUser(userId);
    addEnginesDefaultTenantAuthorizationForUser(userId, engineContext, allAuthorizationsOfUser);

    return resolveUserResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizationsOfUser,
      RELEVANT_PERMISSIONS,
      RESOURCE_TYPE_TENANT
    );
  }

  private ResolvedResourceTypeAuthorizations resolveGroupAuthorizations(final String groupId,
                                                                        final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizations = engineContext.getAllTenantAuthorizations();
    addEnginesDefaultTenantAuthorizationForGroup(groupId, engineContext, allAuthorizations);

    return resolveResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      RELEVANT_PERMISSIONS,
      Collections.singletonList(groupId),
      RESOURCE_TYPE_TENANT
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
