/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_REVOKE;

public abstract class AbstractCachingAuthorizationService<T> implements SessionListener, ConfigurationReloadable {
  private static final int CACHE_MAXIMUM_SIZE = 1000;

  protected final EngineContextFactory engineContextFactory;
  protected final ConfigurationService configurationService;

  protected LoadingCache<String, T> userAuthorizationLoadingCache;
  protected LoadingCache<String, T> groupAuthorizationLoadingCache;

  public AbstractCachingAuthorizationService(final EngineContextFactory engineContextFactory,
                                             final ConfigurationService configurationService) {
    this.engineContextFactory = engineContextFactory;
    this.configurationService = configurationService;

    initState();
  }

  @Override
  public void onSessionCreate(final String userId) {
    onSessionCreateOrRefresh(userId);
  }

  @Override
  public void onSessionRefresh(final String userId) {
    onSessionCreateOrRefresh(userId);
  }

  private void onSessionCreateOrRefresh(final String identityId) {
    // invalidate to force removal of old entry synchronously
    userAuthorizationLoadingCache.invalidate(identityId);
    // trigger eager load of authorizations when new session is created
    userAuthorizationLoadingCache.refresh(identityId);
  }

  @Override
  public void onSessionDestroy(final String identityId) {
    userAuthorizationLoadingCache.invalidate(identityId);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initState();
  }

  protected void initState() {
    initAuthorizationsCache();
  }

  protected abstract T fetchAuthorizationsForUserId(final String userId);

  protected abstract T fetchAuthorizationsForGroupId(final String userId);

  private void initAuthorizationsCache() {
    userAuthorizationLoadingCache = Caffeine.newBuilder()
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .expireAfterAccess(configurationService.getTokenLifeTimeMinutes(), TimeUnit.MINUTES)
      .build(this::fetchAuthorizationsForUserId);

    groupAuthorizationLoadingCache = Caffeine.newBuilder()
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .expireAfterAccess(configurationService.getTokenLifeTimeMinutes(), TimeUnit.MINUTES)
      .build(this::fetchAuthorizationsForGroupId);
  }

  public static ResolvedResourceTypeAuthorizations resolveResourceAuthorizations(final String engine,
                                                                                 final List<AuthorizationDto> allEngineAuthorizations,
                                                                                 final List<String> relevantPermissions,
                                                                                 final String userId,
                                                                                 final List<GroupDto> userGroups,
                                                                                 final int resourceType) {
    return resolveResourceAuthorizations(
      mapToEngineAuthorizations(engine, allEngineAuthorizations, userId, userGroups),
      relevantPermissions,
      resourceType
    );
  }

  public static ResolvedResourceTypeAuthorizations resolveResourceAuthorizations(final String engine,
                                                                                 final List<AuthorizationDto> allEngineAuthorizations,
                                                                                 final List<String> relevantPermissions,
                                                                                 final List<GroupDto> groups,
                                                                                 final int resourceType) {
    return resolveResourceAuthorizations(
      mapToEngineAuthorizations(engine, allEngineAuthorizations, groups),
      relevantPermissions,
      resourceType
    );
  }

  public static ResolvedResourceTypeAuthorizations resolveResourceAuthorizations(final EngineAuthorizations resourceAuthorizations,
                                                                                 final List<String> relevantPermissions,
                                                                                 final int resourceType) {
    final ResolvedResourceTypeAuthorizations authorizations = new ResolvedResourceTypeAuthorizations();
    // NOTE: the order is essential here to make sure that
    // the revoking of resource permissions works correctly

    authorizations.setEngine(resourceAuthorizations.getEngine());

    // global authorizations
    resourceAuthorizations.getGlobalAuthorizations().forEach(
      authorization -> addGloballyAuthorizedResources(authorization, relevantPermissions, authorizations, resourceType)
    );

    // group authorizations
    addResourceAuthorizations(
      resourceAuthorizations.getGroupAuthorizations(), relevantPermissions, authorizations, resourceType
    );

    // user authorizations
    addResourceAuthorizations(
      resourceAuthorizations.getUserAuthorizations(), relevantPermissions, authorizations, resourceType
    );
    return authorizations;
  }

  public static EngineAuthorizations mapToEngineAuthorizations(final String engine,
                                                               final List<AuthorizationDto> allEngineAuthorizations,
                                                               final String userId,
                                                               final List<GroupDto> userGroups) {
    return new EngineAuthorizations(
      engine,
      extractGlobalAuthorizations(allEngineAuthorizations),
      extractGroupAuthorizations(userGroups, allEngineAuthorizations),
      extractUserAuthorizations(userId, allEngineAuthorizations)
    );
  }

  public static EngineAuthorizations mapToEngineAuthorizations(final String engine,
                                                               final List<AuthorizationDto> allEngineAuthorizations,
                                                               final List<GroupDto> groups) {
    return new EngineAuthorizations(
      engine,
      extractGlobalAuthorizations(allEngineAuthorizations),
      extractGroupAuthorizations(groups, allEngineAuthorizations),
      Collections.EMPTY_LIST
    );
  }

  private static List<AuthorizationDto> extractGlobalAuthorizations(final List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(authorization -> authorization.getType() == AUTHORIZATION_TYPE_GLOBAL)
      .collect(Collectors.toList());
  }

  private static List<AuthorizationDto> extractGroupAuthorizations(final List<GroupDto> groupsOfUser,
                                                                   final List<AuthorizationDto> allAuthorizations) {
    final Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private static List<AuthorizationDto> extractUserAuthorizations(final String username,
                                                                  final List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> username.equals(a.getUserId()))
      .collect(Collectors.toList());
  }

  private static void addResourceAuthorizations(final List<AuthorizationDto> groupAuthorizations,
                                                final List<String> relevantPermissions,
                                                final ResolvedResourceTypeAuthorizations resolvedAuthorizations,
                                                final int resourceType) {
    groupAuthorizations.forEach(
      authDto -> removeRevokedAuthorizations(authDto, relevantPermissions, resolvedAuthorizations, resourceType)
    );
    groupAuthorizations.forEach(
      authDto -> addGrantedAuthorizations(authDto, relevantPermissions, resolvedAuthorizations, resourceType)
    );
    groupAuthorizations.forEach(
      authDto -> removeAuthorizationForProhibitedResource(
        authDto, relevantPermissions, resolvedAuthorizations, resourceType
      ));
    groupAuthorizations.forEach(
      authDto -> addAuthorizationForResources(authDto, relevantPermissions, resolvedAuthorizations, resourceType)
    );
  }

  private static void addGloballyAuthorizedResources(final AuthorizationDto authorization,
                                                     final List<String> relevantPermissions,
                                                     final ResolvedResourceTypeAuthorizations resourceAuthorizations,
                                                     final int resourceType) {
    boolean hasPermissions = hasRelevantPermissions(authorization, relevantPermissions);
    boolean globalGrantPermission = authorization.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && globalGrantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.grantToSeeAllResources();
      } else if (!resourceId.isEmpty()) {
        resourceAuthorizations.authorizeResource(resourceId);
      }
    }
  }

  private static void addGrantedAuthorizations(final AuthorizationDto authorization,
                                               final List<String> relevantPermissions,
                                               final ResolvedResourceTypeAuthorizations resourceAuthorizations,
                                               final int resourceType) {
    boolean hasPermissions = hasRelevantPermissions(authorization, relevantPermissions);
    boolean grantPermission = authorization.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.grantToSeeAllResources();
      }
    }
  }

  private static void addAuthorizationForResources(final AuthorizationDto authorization,
                                                   final List<String> relevantPermissions,
                                                   final ResolvedResourceTypeAuthorizations resourceAuthorizations,
                                                   final int resourceType) {
    boolean hasPermissions = hasRelevantPermissions(authorization, relevantPermissions);
    boolean grantPermission = authorization.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (!resourceId.isEmpty()) {
        resourceAuthorizations.authorizeResource(resourceId);
      }
    }
  }

  private static void removeRevokedAuthorizations(final AuthorizationDto authorization,
                                                  final List<String> relevantPermissions,
                                                  final ResolvedResourceTypeAuthorizations resourceAuthorizations,
                                                  final int resourceType) {
    boolean hasPermissions = hasRelevantPermissions(authorization, relevantPermissions);
    boolean revokePermission = authorization.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.revokeToSeeAllResources();
      }
    }
  }

  private static void removeAuthorizationForProhibitedResource(final AuthorizationDto authorization,
                                                               final List<String> relevantPermissions,
                                                               final ResolvedResourceTypeAuthorizations resourceAuthorizations,
                                                               final int resourceType) {
    boolean hasPermissions = hasRelevantPermissions(authorization, relevantPermissions);
    boolean revokePermission = authorization.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (!resourceId.isEmpty()) {
        resourceAuthorizations.prohibitResource(resourceId);
      }
    }
  }

  private static boolean hasRelevantPermissions(final AuthorizationDto authorization,
                                                final List<String> relevantPermissions) {
    return authorization.getPermissions().stream().anyMatch(relevantPermissions::contains);
  }

  public T getCachedAuthorizationsForId(final String identityId, final IdentityType identityType) {
    switch (identityType) {
      case USER:
        return userAuthorizationLoadingCache.get(identityId);
      case GROUP:
        return groupAuthorizationLoadingCache.get(identityId);
      default:
        throw new OptimizeRuntimeException("Unsupported IdentityType: " + identityType);
    }
  }
}
