/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;

@Component
public class IdentityAuthorizationService
  extends AbstractCachingAuthorizationService<Map<IdentityType, ResolvedResourceTypeAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_PERMISSION);

  private final ApplicationAuthorizationService applicationAuthorizationService;

  public IdentityAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                      final EngineContextFactory engineContextFactory,
                                      final ConfigurationService configurationService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
  }

  public boolean isUserAuthorizedToSeeIdentity(final String userId,
                                               final IdentityType requestedIdentityType,
                                               final String requestedIdentityId) {
    return isAuthorizedToSeeIdentity(IdentityType.USER, userId, requestedIdentityType, requestedIdentityId);
  }

  private boolean isAuthorizedToSeeIdentity(final IdentityType identityType,
                                            final String identityId,
                                            final IdentityType requestedIdentityType,
                                            final String requestedIdentityId) {
    final Map<IdentityType, ResolvedResourceTypeAuthorizations> identityAuthorizations =
      getCachedAuthorizationsForId(identityId, identityType);
    return identityAuthorizations
      .get(requestedIdentityType)
      .isAuthorizedToAccessResource(requestedIdentityId);
  }

  @Override
  protected Map<IdentityType, ResolvedResourceTypeAuthorizations> fetchAuthorizationsForUserId(final String userId) {
    final Map<IdentityType, ResolvedResourceTypeAuthorizations> result = new EnumMap<>(IdentityType.class);
    result.put(IdentityType.USER, fetchIdentityAuthorizationsForUserId(IdentityType.USER, userId));
    result.put(IdentityType.GROUP, fetchIdentityAuthorizationsForUserId(IdentityType.GROUP, userId));
    return result;
  }

  @Override
  protected Map<IdentityType, ResolvedResourceTypeAuthorizations> fetchAuthorizationsForGroupId(final String groupId) {
    final Map<IdentityType, ResolvedResourceTypeAuthorizations> result = new EnumMap<>(IdentityType.class);
    result.put(IdentityType.USER, fetchIdentityAuthorizationsForGroupId(IdentityType.USER, groupId));
    result.put(IdentityType.GROUP, fetchIdentityAuthorizationsForGroupId(IdentityType.GROUP, groupId));
    return result;
  }

  private ResolvedResourceTypeAuthorizations fetchIdentityAuthorizationsForUserId(final IdentityType identitytype,
                                                                                  final String userId) {
    ResolvedResourceTypeAuthorizations authorizations = new ResolvedResourceTypeAuthorizations();
    List<String> engineAliases = applicationAuthorizationService.getAuthorizedEnginesForUser(userId);
    for (String engineAlias : engineAliases) {
      engineContextFactory.getConfiguredEngineByAlias(engineAlias).ifPresent(
        engineContext -> authorizations.merge(resolveUserAuthorizations(identitytype, userId, engineContext))
      );
    }
    return authorizations;
  }

  private ResolvedResourceTypeAuthorizations fetchIdentityAuthorizationsForGroupId(final IdentityType identitytype,
                                                                                   final String groupId) {
    ResolvedResourceTypeAuthorizations authorizations = new ResolvedResourceTypeAuthorizations();
    List<String> engineAliases = applicationAuthorizationService.getAuthorizedEnginesForUser(groupId);
    for (String engineAlias : engineAliases) {
      engineContextFactory.getConfiguredEngineByAlias(engineAlias).ifPresent(
        engineContext -> authorizations.merge(resolveGroupAuthorizations(identitytype, groupId, engineContext))
      );
    }
    return authorizations;
  }

  private ResolvedResourceTypeAuthorizations resolveUserAuthorizations(final IdentityType identitytype,
                                                                       final String userId,
                                                                       final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizations = getAllIdentityAuthorizationsForUser(
      engineContext,
      identitytype,
      userId
    );

    return resolveUserResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      RELEVANT_PERMISSIONS,
      resolveResourceTypeFromIdentityType(identitytype)
    );
  }

  private ResolvedResourceTypeAuthorizations resolveGroupAuthorizations(final IdentityType identitytype,
                                                                        final String groupId,
                                                                        final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizations = getAllIdentityAuthorizations(engineContext, identitytype);

    return resolveResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      RELEVANT_PERMISSIONS,
      Collections.singletonList(groupId),
      resolveResourceTypeFromIdentityType(identitytype)
    );
  }

  private List<AuthorizationDto> getAllIdentityAuthorizations(final EngineContext engineContext,
                                                              final IdentityType identityType) {
    if (IdentityType.USER.equals(identityType)) {
      return engineContext.getAllUserAuthorizations();
    }
    return engineContext.getAllGroupAuthorizations();
  }

  private List<AuthorizationDto> getAllIdentityAuthorizationsForUser(final EngineContext engineContext,
                                                                     final IdentityType identityType,
                                                                     final String userId) {
    if (IdentityType.USER.equals(identityType)) {
      return engineContext.getAllUserAuthorizationsForUser(userId);
    }
    return engineContext.getAllGroupAuthorizationsForUser(userId);
  }

  private int resolveResourceTypeFromIdentityType(final IdentityType identitytype) {
    if (IdentityType.USER.equals(identitytype)) {
      return RESOURCE_TYPE_USER;
    }
    return RESOURCE_TYPE_GROUP;
  }

}
