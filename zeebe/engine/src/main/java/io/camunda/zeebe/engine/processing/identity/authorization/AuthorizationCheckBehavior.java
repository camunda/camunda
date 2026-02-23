/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.authorization.aggregator.RejectionAggregator;
import io.camunda.zeebe.engine.processing.identity.authorization.property.PropertyAuthorizationEvaluatorRegistry;
import io.camunda.zeebe.engine.processing.identity.authorization.property.ResourceAuthorizationProperties;
import io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.PropertyAuthorizationEvaluator;
import io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.UserTaskPropertyAuthorizationEvaluator;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.authorization.resolver.AuthorizationScopeResolver;
import io.camunda.zeebe.engine.processing.identity.authorization.resolver.ClaimsExtractor;
import io.camunda.zeebe.engine.processing.identity.authorization.resolver.TenantResolver;
import io.camunda.zeebe.engine.processing.identity.authorization.result.AuthorizationRejection;
import io.camunda.zeebe.engine.processing.identity.authorization.result.AuthorizationResult;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AuthorizationCheckBehavior {
  public static final String NOT_FOUND_ERROR_MESSAGE =
      "Expected to %s with key '%s', but no %s was found";
  private static final Either<Rejection, Void> AUTHORIZED = Either.right(null);

  private final MappingRuleState mappingRuleState;
  private final ClaimsExtractor claimsExtractor;
  private final TenantResolver tenantResolver;
  private final AuthorizationScopeResolver scopeResolver;
  private final PropertyAuthorizationEvaluatorRegistry propertyEvaluatorRegistry;

  private final boolean authorizationsEnabled;
  private final boolean multiTenancyEnabled;

  private final LoadingCache<AuthorizationRequest, Either<Rejection, Void>> authorizationsCache;

  public AuthorizationCheckBehavior(
      final ProcessingState processingState,
      final SecurityConfiguration securityConfig,
      final EngineConfiguration config) {
    final var authorizationState = processingState.getAuthorizationState();
    final var membershipState = processingState.getMembershipState();

    mappingRuleState = processingState.getMappingRuleState();
    authorizationsEnabled = securityConfig.getAuthorizations().isEnabled();
    multiTenancyEnabled = securityConfig.getMultiTenancy().isChecksEnabled();
    claimsExtractor = new ClaimsExtractor(membershipState);
    tenantResolver =
        new TenantResolver(membershipState, mappingRuleState, claimsExtractor, multiTenancyEnabled);
    scopeResolver =
        new AuthorizationScopeResolver(
            authorizationState,
            membershipState,
            mappingRuleState,
            claimsExtractor,
            authorizationsEnabled);
    propertyEvaluatorRegistry =
        new PropertyAuthorizationEvaluatorRegistry()
            .register(
                new UserTaskPropertyAuthorizationEvaluator(claimsExtractor, mappingRuleState));

    authorizationsCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(config.getAuthorizationsCacheTtl())
            .maximumSize(config.getAuthorizationsCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public Either<Rejection, Void> load(
                      final AuthorizationRequest authorizationRequest) {
                    return checkAuthorized(authorizationRequest);
                  }
                });
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource. The user key is taken from
   * the authorizations of the command.
   *
   * <p>The caller of this method should provide an {@link AuthorizationRequest}. This object
   * contains the data required to do the check.
   *
   * @param request the authorization request to check authorization for. This contains the claims,
   *     the resource type, the permission type, a set of resource identifiers and the tenant id
   * @return a {@link Either} containing a {@link RejectionType} if the user is not authorized or
   *     {@link Void} if the user is authorized
   */
  public Either<Rejection, Void> isAuthorized(final AuthorizationRequest request) {
    try {
      return authorizationsCache.get(request);
    } catch (final ExecutionException e) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              "No authorization data was found for the provided authorization claims."));
    }
  }

  /**
   * Checks if a user is authorized through ANY of the provided authorization requests. Returns
   * success if at least one request is authorized (OR/disjunctive logic).
   *
   * <p>Note: This method does NOT skip internal commands. Use {@link
   * #isAnyAuthorizedOrInternalCommand(AuthorizationRequest...)} if you need to skip authorization
   * for internal commands.
   *
   * @param requests the authorization requests to check (at least one must pass)
   * @return Either containing a rejection if ALL requests failed, or Void if any succeeded
   */
  public Either<Rejection, Void> isAnyAuthorized(final AuthorizationRequest... requests) {
    if (requests == null || requests.length == 0) {
      throw new IllegalArgumentException("No authorization requests provided");
    }

    final List<Rejection> rejections = new ArrayList<>();

    for (final var request : requests) {
      final var result = isAuthorized(request);
      if (result.isRight()) {
        return AUTHORIZED;
      }
      rejections.add(result.getLeft());
    }

    return Either.left(RejectionAggregator.aggregateComposite(rejections));
  }

  /**
   * Checks if a user is authorized to perform an action on a resource, or if the request is
   * triggered by an internal command (in which case authorization is bypassed).
   *
   * @param request the authorization request to check authorization for
   * @return a {@link Either} containing a {@link Rejection} if the user is not authorized or {@link
   *     Void} if the user is authorized or if triggered by an internal command
   */
  public Either<Rejection, Void> isAuthorizedOrInternalCommand(final AuthorizationRequest request) {
    return isAnyAuthorizedOrInternalCommand(request);
  }

  /**
   * Checks if a user is authorized through ANY of the provided authorization requests, or if the
   * request is triggered by an internal command (in which case authorization is bypassed). Returns
   * success if at least one request is authorized (OR/disjunctive logic).
   *
   * @param requests the authorization requests to check (at least one must pass)
   * @return Either containing a rejection (left) if ALL requests failed, or Void (right) if any
   *     succeeded or if triggered by an internal command
   */
  public Either<Rejection, Void> isAnyAuthorizedOrInternalCommand(
      final AuthorizationRequest... requests) {
    if (requests == null || requests.length == 0) {
      throw new IllegalArgumentException("No authorization requests provided");
    }

    // bypass authorization if any request is from an internal command
    for (final var request : requests) {
      if (request.isTriggeredByInternalCommand()) {
        return AUTHORIZED;
      }
    }

    return isAnyAuthorized(requests);
  }

  private Either<Rejection, Void> checkAuthorized(final AuthorizationRequest request) {
    if (shouldSkipAuthorization(request)) {
      return AUTHORIZED;
    }

    final List<AuthorizationRejection> aggregatedRejections = new ArrayList<>();

    // Step 1: Check primary entity (user/client)
    final AuthorizationResult primaryResult =
        checkPrimaryAuthorization(request, aggregatedRejections);
    if (primaryResult.hasBothAccess()) {
      return AUTHORIZED;
    }

    // Step 2: Check mapping rules (can supplement primary entity)
    final AuthorizationResult mappingRuleResult =
        checkMappingRuleAuthorization(request, primaryResult, aggregatedRejections);
    if (mappingRuleResult.hasBothAccess()) {
      return AUTHORIZED;
    }

    // Step 3: Check if request can be authorized by resource properties
    // Only if we have tenant access (from primary or mapping rules)
    if (request.hasResourceProperties() && mappingRuleResult.hasTenantAccess()) {
      if (isAuthorizedByProperties(request, aggregatedRejections)) {
        return AUTHORIZED;
      }
    }

    return Either.left(RejectionAggregator.aggregate(aggregatedRejections));
  }

  // Helper methods
  private boolean shouldSkipAuthorization(final AuthorizationRequest request) {
    return (!authorizationsEnabled && !multiTenancyEnabled)
        || claimsExtractor.isAuthorizedAnonymousUser(request.claims());
  }

  private AuthorizationResult checkPrimaryAuthorization(
      final AuthorizationRequest request, final List<AuthorizationRejection> aggregatedRejections) {
    final var username = claimsExtractor.getUsername(request);
    final var clientId = claimsExtractor.getClientId(request);
    if (clientId.isPresent()) {
      return checkAccessForEntity(request, EntityType.CLIENT, clientId.get(), aggregatedRejections);
    } else if (username.isPresent()) {
      return checkAccessForEntity(request, EntityType.USER, username.get(), aggregatedRejections);
    }
    return new AuthorizationResult(false, false);
  }

  private AuthorizationResult checkAccessForEntity(
      final AuthorizationRequest request,
      final EntityType entityType,
      final String entityId,
      final List<AuthorizationRejection> aggregatedRejections) {
    final boolean resourceAccess =
        hasAccess(isEntityAuthorized(request, entityType, Set.of(entityId)), aggregatedRejections);
    final boolean tenantAccess =
        hasAccess(isTenantAssigned(request, entityType, Set.of(entityId)), aggregatedRejections);
    return new AuthorizationResult(tenantAccess, resourceAccess);
  }

  private AuthorizationResult checkMappingRuleAuthorization(
      final AuthorizationRequest request,
      final AuthorizationResult primaryResult,
      final List<AuthorizationRejection> aggregatedRejections) {

    final Set<String> mappingRules =
        getPersistedMappingRules(request)
            .map(PersistedMappingRule::getMappingRuleId)
            .collect(Collectors.toSet());

    boolean tenantAccess = primaryResult.hasTenantAccess();
    boolean resourceAccess = primaryResult.hasResourceAccess();

    if (!tenantAccess) {
      tenantAccess =
          hasAccess(
              isTenantAssigned(request, EntityType.MAPPING_RULE, mappingRules),
              aggregatedRejections);
    }

    if (!resourceAccess && tenantAccess) {
      resourceAccess =
          hasAccess(
              isEntityAuthorized(request, EntityType.MAPPING_RULE, mappingRules),
              aggregatedRejections);
    }
    return new AuthorizationResult(tenantAccess, resourceAccess);
  }

  private boolean isAuthorizedByProperties(
      final AuthorizationRequest request, final List<AuthorizationRejection> aggregatedRejections) {

    final var authorizationRejection =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage()));

    final var evaluator = propertyEvaluatorRegistry.get(request.resourceType());
    if (evaluator.isEmpty()) {
      // No evaluator for this resource type - cannot grant access via properties
      aggregatedRejections.add(authorizationRejection);
      return false;
    }

    final Set<String> matchedProperties =
        matchProperties(evaluator.get(), request.claims(), request.resourceProperties());

    if (matchedProperties.isEmpty()) {
      aggregatedRejections.add(authorizationRejection);
      return false;
    }

    final var authorizedScopes = getPropertyAuthorizedScopes(request);
    final boolean hasMatchingScope =
        authorizedScopes.stream()
            .anyMatch(scope -> matchedProperties.contains(scope.getResourcePropertyName()));

    if (!hasMatchingScope) {
      aggregatedRejections.add(authorizationRejection);
      return false;
    }

    return true;
  }

  private <T extends ResourceAuthorizationProperties> Set<String> matchProperties(
      final PropertyAuthorizationEvaluator<T> evaluator,
      final Map<String, Object> claims,
      final ResourceAuthorizationProperties properties) {

    final Class<T> expectedType = evaluator.propertiesType();

    if (!expectedType.isInstance(properties)) {
      throw new IllegalStateException(
          "Evaluator %s expects %s but received %s"
              .formatted(
                  evaluator.getClass().getSimpleName(),
                  expectedType.getSimpleName(),
                  properties.getClass().getSimpleName()));
    }

    final T typedProperties = expectedType.cast(properties);
    return evaluator.evaluateMatchingProperties(claims, typedProperties);
  }

  private Set<AuthorizationScope> getPropertyAuthorizedScopes(final AuthorizationRequest request) {
    return scopeResolver.getAllAuthorizedScopes(request).stream()
        .filter(scope -> scope.getMatcher() == AuthorizationResourceMatcher.PROPERTY)
        .collect(Collectors.toSet());
  }

  private boolean hasAccess(
      final Either<AuthorizationRejection, Void> request,
      final List<AuthorizationRejection> aggregatedRejections) {
    if (request.isLeft()) {
      aggregatedRejections.add(request.getLeft());
    }
    return request.isRight();
  }

  private Either<AuthorizationRejection, Void> isTenantAssigned(
      final AuthorizationRequest request, final EntityType entityType, final Set<String> owners) {
    if (multiTenancyEnabled && request.isTenantOwnedResource()) {
      final var notAssignedToTenant =
          owners.stream()
              .noneMatch(
                  entity ->
                      tenantResolver
                          .getTenantIdsForEntity(request.claims(), entityType, entity)
                          .anyMatch(request.tenantId()::equals));
      if (notAssignedToTenant) {
        final var rejectionType =
            request.isNewResource() ? RejectionType.FORBIDDEN : RejectionType.NOT_FOUND;
        return Either.left(
            new AuthorizationRejection.Tenant(
                new Rejection(rejectionType, request.getTenantErrorMessage())));
      }
    }
    return Either.right(null);
  }

  /**
   * Verifies a user is authorized to perform this request. This method checks if the user has
   * access to the tenant and if the user has the required permissions for the resource.
   *
   * @param request the authorization request to check authorization for
   * @param entityType the type of the entity being accessed
   * @param entityIds the username of the user making this request
   * @return an {@link Either} containing a {@link Rejection} or {@link Void}
   */
  private Either<AuthorizationRejection, Void> isEntityAuthorized(
      final AuthorizationRequest request,
      final EntityType entityType,
      final Collection<String> entityIds) {

    if (!authorizationsEnabled) {
      return Either.right(null);
    }

    final var grantedScopes =
        entityIds.stream()
            .flatMap(
                entityId ->
                    scopeResolver.getScopesForEntity(
                        request.claims(),
                        entityType,
                        entityId,
                        request.resourceType(),
                        request.permissionType()))
            .collect(Collectors.toSet());

    if (isAuthorizedByScopes(request, grantedScopes)) {
      return Either.right(null);
    }

    return Either.left(
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage())));
  }

  private boolean isAuthorizedByScopes(
      final AuthorizationRequest request, final Set<AuthorizationScope> grantedScopes) {

    if (grantedScopes.isEmpty()) {
      return false;
    }

    // wildcard grants access to all resources
    if (grantedScopes.contains(AuthorizationScope.WILDCARD)) {
      return true;
    }

    // no specific resources requested = need wildcard (already checked above)
    if (request.resourceIds().isEmpty()) {
      return false;
    }

    // check if any granted scope matches any requested `resourceId`
    return grantedScopes.stream()
        .filter(scope -> scope.getMatcher() == AuthorizationResourceMatcher.ID)
        .map(AuthorizationScope::getResourceId)
        .filter(resourceId -> resourceId != null && !resourceId.isEmpty())
        .anyMatch(request.resourceIds()::contains);
  }

  /**
   * Get all authorized authorization scopes for a given authorization request.
   *
   * <p>This method aggregates scopes from the authenticated user/client and any matching mapping
   * rules. If authorizations are disabled or the user is anonymous, returns wildcard scope.
   *
   * @param request the authorization request containing claims, resource type, and permission type
   * @return a set of authorized scopes for the request
   */
  public Set<AuthorizationScope> getAllAuthorizedScopes(final AuthorizationRequest request) {
    return scopeResolver.getAllAuthorizedScopes(request);
  }

  /**
   * Get direct authorized authorization scopes for a given owner, resource type and permission
   * type. This does not include inherited authorizations, for example authorizations for users from
   * assigned roles or groups.
   */
  public Set<AuthorizationScope> getDirectAuthorizedAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return scopeResolver.getDirectScopes(ownerType, ownerId, resourceType, permissionType);
  }

  /**
   * Checks if a user is assigned to a specific tenant. If multi-tenancy is disabled, this method
   * will always return true.
   *
   * @param command The command send by the user
   * @param tenantId The tenant we want to check assignment for
   * @return true if assigned or multi-tenancy is disabled, false otherwise
   */
  public boolean isAssignedToTenant(final TypedRecord<?> command, final String tenantId) {
    return tenantResolver.isAssignedToTenant(command.getAuthorizations(), tenantId);
  }

  /**
   * Retrieves all authorized tenants for the given command. Includes tenants from user/client,
   * groups, roles, and mapping rules.
   *
   * @param command the command containing authorization claims
   * @return an {@link AuthorizedTenants} object containing all tenant IDs the principal is
   *     authorized to access
   */
  public AuthorizedTenants getAuthorizedTenantIds(final TypedRecord<?> command) {
    return getAuthorizedTenantIds(command.getAuthorizations());
  }

  /**
   * Retrieves all authorized tenants for the given claims. Includes tenants from user/client,
   * groups, roles, and mapping rules.
   *
   * @param claims the authorization claims
   * @return an {@link AuthorizedTenants} object containing all tenant IDs the principal is
   *     authorized to access
   */
  public AuthorizedTenants getAuthorizedTenantIds(final Map<String, Object> claims) {
    return tenantResolver.getAuthorizedTenants(claims);
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final AuthorizationRequest request) {
    final var claims = claimsExtractor.getTokenClaims(request.claims());
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), claims);
  }

  public void clearAuthorizationsCache() {
    authorizationsCache.invalidateAll();
  }
}
