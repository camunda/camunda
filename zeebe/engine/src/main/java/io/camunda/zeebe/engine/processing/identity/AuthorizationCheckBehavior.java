/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AuthorizationCheckBehavior {

  public static final String FORBIDDEN_ERROR_MESSAGE =
      "Insufficient permissions to perform operation '%s' on resource '%s'";
  public static final String FORBIDDEN_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s' for tenant '%s', but user is not assigned to this tenant";
  public static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE =
      FORBIDDEN_ERROR_MESSAGE + ", required resource identifiers are one of '%s'";
  public static final String NOT_FOUND_ERROR_MESSAGE =
      "Expected to %s with key '%s', but no %s was found";
  public static final String NOT_FOUND_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";
  private final AuthorizationState authorizationState;
  private final MappingRuleState mappingRuleState;
  private final MembershipState membershipState;

  private final boolean authorizationsEnabled;
  private final boolean multiTenancyEnabled;

  private final LoadingCache<AuthorizationRequestMetadata, Either<Rejection, Void>>
      authorizationsCache;

  public AuthorizationCheckBehavior(
      final ProcessingState processingState,
      final SecurityConfiguration securityConfig,
      final EngineConfiguration config) {
    authorizationState = processingState.getAuthorizationState();
    mappingRuleState = processingState.getMappingRuleState();
    membershipState = processingState.getMembershipState();
    authorizationsEnabled = securityConfig.getAuthorizations().isEnabled();
    multiTenancyEnabled = securityConfig.getMultiTenancy().isChecksEnabled();

    authorizationsCache =
        CacheBuilder.newBuilder()
            .maximumSize(config.getAuthorizationsCacheCapacity())
            .build(
                new CacheLoader<>() {
                  @Override
                  public Either<Rejection, Void> load(
                      final AuthorizationRequestMetadata authorizationRequest)
                      throws AuthorizationsNotFoundException {
                    return checkAuthorized(authorizationRequest);
                  }
                });
  }

  /**
   * @deprecated Please use {@link #isAuthorized(AuthorizationRequestMetadata)} instead. The {@link
   *     AuthorizationRequest} class will be refactored into a builder class.
   *     <p>Checks if a user is Authorized to perform an action on a resource. The user key is taken
   *     from the authorizations of the command.
   *     <p>The caller of this method should provide an {@link AuthorizationRequest}. This object
   *     contains the data required to do the check.
   * @param requestBuilder the builder for the authorization request to check authorization for. his
   *     contains the command, the resource type, the permission type, a set of resource identifiers
   *     and the tenant id.
   * @return a {@link Either} containing a {@link RejectionType} if the user is not authorized or
   *     {@link Void} if the user is authorized
   */
  @Deprecated(forRemoval = true, since = "8.8.0")
  public Either<Rejection, Void> isAuthorized(final AuthorizationRequest requestBuilder) {
    return isAuthorized(requestBuilder.build());
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
  public Either<Rejection, Void> isAuthorized(final AuthorizationRequestMetadata request) {
    try {
      return authorizationsCache.get(request);
    } catch (final ExecutionException e) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              "No authorization data was found for the provided authorization claims."));
    }
  }

  public Either<Rejection, Void> isAuthorizedOrInternalCommand(final AuthorizationRequest request) {
    final var command = request.command;
    if (isInternalCommand(command.hasRequestMetadata(), command.getBatchOperationReference())) {
      return Either.right(null);
    }
    return isAuthorized(request);
  }

  private Either<Rejection, Void> checkAuthorized(final AuthorizationRequestMetadata request) {

    if (shouldSkipAuthorization(request)) {
      return Either.right(null);
    }

    final List<AuthorizationRejection> aggregatedRejections = new ArrayList<>();

    final AuthorizationResult primaryResult =
        checkPrimaryAuthorization(request, aggregatedRejections);

    if (primaryResult.hasBothAccess()) {
      return Either.right(null);
    }

    final AuthorizationResult mappingRuleResult =
        checkMappingRuleAuthorization(request, primaryResult, aggregatedRejections);

    if (mappingRuleResult.hasBothAccess()) {
      return Either.right(null);
    }

    return getRejection(aggregatedRejections);
  }

  // Helper methods
  private boolean shouldSkipAuthorization(final AuthorizationRequestMetadata request) {
    return (!authorizationsEnabled && !multiTenancyEnabled)
        || isAuthorizedAnonymousUser(request.claims());
  }

  /**
   * Determines if the command is an internal command. Internal commands are commands that are
   * written by Zeebe itself and not by an external user. Internal commands should skip the
   * authorization checks and the tenant checks.
   *
   * <p>A command is considered internal if it has no request metadata and no batch operation
   * reference.
   *
   * @param hasRequestMetadata true if the command has request metadata, false otherwise
   * @param batchOperationReference the batch operation reference of the command
   * @return true if the command is an internal command, false otherwise
   */
  public boolean isInternalCommand(
      final boolean hasRequestMetadata, final long batchOperationReference) {
    return !hasRequestMetadata && batchOperationReference == batchOperationReferenceNullValue();
  }

  private AuthorizationResult checkPrimaryAuthorization(
      final AuthorizationRequestMetadata request,
      final List<AuthorizationRejection> aggregatedRejections) {
    final var username = getUsername(request);
    final var clientId = getClientId(request);
    if (clientId.isPresent()) {
      return checkAccessForEntity(request, EntityType.CLIENT, clientId.get(), aggregatedRejections);
    } else if (username.isPresent()) {
      return checkAccessForEntity(request, EntityType.USER, username.get(), aggregatedRejections);
    }
    return new AuthorizationResult(false, false);
  }

  private AuthorizationResult checkAccessForEntity(
      final AuthorizationRequestMetadata request,
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
      final AuthorizationRequestMetadata request,
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

  private boolean hasAccess(
      final Either<AuthorizationRejection, Void> request,
      final List<AuthorizationRejection> aggregatedRejections) {
    if (request.isLeft()) {
      aggregatedRejections.add(request.getLeft());
    }
    return request.isRight();
  }

  private Either<AuthorizationRejection, Void> isTenantAssigned(
      final AuthorizationRequestMetadata request,
      final EntityType entityType,
      final Set<String> owners) {
    if (multiTenancyEnabled && request.isTenantOwnedResource()) {
      final var notAssignedToTenant =
          owners.stream()
              .noneMatch(
                  entity ->
                      getAuthorizedTenantIds(request.claims(), entityType, entity)
                          .anyMatch(request.tenantId::equals));
      if (notAssignedToTenant) {
        final var rejectionType =
            request.isNewResource() ? RejectionType.FORBIDDEN : RejectionType.NOT_FOUND;
        return Either.left(
            new AuthorizationRejection(
                new Rejection(rejectionType, request.getTenantErrorMessage()),
                AuthorizationRejectionType.TENANT));
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
      final AuthorizationRequestMetadata request,
      final EntityType entityType,
      final Collection<String> entityIds) {

    if (!authorizationsEnabled) {
      return Either.right(null);
    }

    final var isAuthorizedForResource =
        entityIds.stream()
            .flatMap(
                entityId ->
                    getAuthorizedScopes(
                        request.claims(),
                        entityType,
                        entityId,
                        request.resourceType(),
                        request.permissionType()))
            .anyMatch(
                authorizationScope -> request.authorizationScopes().contains(authorizationScope));
    if (isAuthorizedForResource) {
      return Either.right(null);
    }

    return Either.left(
        new AuthorizationRejection(
            new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage()),
            AuthorizationRejectionType.PERMISSION));
  }

  /**
   * Returns a rejection based on the collected rejections. It prioritizes permission rejections
   * first, then tenant rejections, and finally returns the first rejection if no specific type is
   * found.
   *
   * @param rejections the list of collected authorization rejections
   * @return an {@link Either} containing a {@link Rejection} or {@link Void}
   */
  private Either<Rejection, Void> getRejection(final List<AuthorizationRejection> rejections) {
    // return permission rejection first, if it exists
    final var permissionRejections =
        rejections.stream()
            .filter(r -> r.authorizationRejectionType() == AuthorizationRejectionType.PERMISSION)
            .map(AuthorizationRejection::rejection)
            .toList();
    if (!permissionRejections.isEmpty()) {
      final var reason =
          permissionRejections.stream()
              .map(Rejection::reason)
              .distinct()
              .collect(Collectors.joining("; "));
      return Either.left(new Rejection(RejectionType.FORBIDDEN, reason));
    }

    // if there are tenant rejections, return them
    final var tenantRejections =
        rejections.stream()
            .filter(r -> r.authorizationRejectionType() == AuthorizationRejectionType.TENANT)
            .map(AuthorizationRejection::rejection)
            .toList();
    if (!tenantRejections.isEmpty()) {
      final var reason =
          tenantRejections.stream()
              .map(Rejection::reason)
              .distinct()
              .collect(Collectors.joining("; "));
      // Use the first rejection type (should be FORBIDDEN or NOT_FOUND)
      return Either.left(new Rejection(tenantRejections.get(0).type(), reason));
    }

    // Fallback: return the first rejection if present
    if (!rejections.isEmpty()) {
      return Either.left(rejections.get(0).rejection());
    }

    // Should not happen, but fallback to forbidden
    return Either.left(
        new Rejection(RejectionType.FORBIDDEN, "Authorization failed for unknown reason"));
  }

  /**
   * Returns true, if a command is executed by an anonymous authentication. So that, no
   * authorization checks and no tenants checks are performed. Basically, it allows to run commands
   * without authentication safely.
   *
   * <p>This provides anyone, i.e., when doing a broker request, with the option to run broker
   * requests and commands anonymously. This is helpful especially if the gateway, for example,
   * already checks authorizations and tenancy.
   */
  private boolean isAuthorizedAnonymousUser(final Map<String, Object> authorizationClaims) {
    final var authorizedAnonymousUserClaim =
        authorizationClaims.get(Authorization.AUTHORIZED_ANONYMOUS_USER);
    return Optional.ofNullable(authorizedAnonymousUserClaim).map(Boolean.class::cast).orElse(false);
  }

  private Optional<String> getUsername(final AuthorizationRequestMetadata request) {
    return getUsername(request.claims());
  }

  private Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  private Optional<String> getClientId(final AuthorizationRequestMetadata request) {
    return getClientId(request.claims());
  }

  private Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }

  private Stream<String> getAuthorizedTenantIds(
      final Map<String, Object> authorizations,
      final EntityType entityType,
      final String entityId) {
    return Stream.concat(
        membershipState.getMemberships(entityType, entityId, RelationType.TENANT).stream(),
        Stream.concat(
            fetchGroups(authorizations, entityType, entityId).stream()
                .flatMap(
                    groupId ->
                        Stream.concat(
                            membershipState
                                .getMemberships(EntityType.GROUP, groupId, RelationType.TENANT)
                                .stream(),
                            membershipState
                                .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                                .stream()
                                .flatMap(
                                    roleId ->
                                        membershipState
                                            .getMemberships(
                                                EntityType.ROLE, roleId, RelationType.TENANT)
                                            .stream()))),
            membershipState.getMemberships(entityType, entityId, RelationType.ROLE).stream()
                .flatMap(
                    roleId ->
                        membershipState
                            .getMemberships(EntityType.ROLE, roleId, RelationType.TENANT)
                            .stream())));
  }

  public Set<AuthorizationScope> getAllAuthorizedScopes(final AuthorizationRequest request) {
    return getAllAuthorizedScopes(request.build());
  }

  public Set<AuthorizationScope> getAllAuthorizedScopes(
      final AuthorizationRequestMetadata request) {
    if (!authorizationsEnabled || isAuthorizedAnonymousUser(request.claims())) {
      return Set.of(AuthorizationScope.WILDCARD);
    }

    final var authorizedScopes = new HashSet<AuthorizationScope>();

    final var optionalClientId = getClientId(request);
    if (optionalClientId.isPresent()) {
      getAuthorizedScopes(
              request.claims(),
              EntityType.CLIENT,
              optionalClientId.get(),
              request.resourceType(),
              request.permissionType())
          .forEach(authorizedScopes::add);
    }
    // If a clientId was present, don't use the username
    else {
      getUsername(request)
          .map(
              username ->
                  getAuthorizedScopes(
                      request.claims(),
                      EntityType.USER,
                      username,
                      request.resourceType(),
                      request.permissionType()))
          .ifPresent(idsForUsername -> idsForUsername.forEach(authorizedScopes::add));
    }

    // mapping rules can layer on top of username/client id
    getPersistedMappingRules(request)
        .flatMap(
            mappingRule ->
                getAuthorizedScopes(
                    request.claims(),
                    EntityType.MAPPING_RULE,
                    mappingRule.getMappingRuleId(),
                    request.resourceType(),
                    request.permissionType()))
        .forEach(authorizedScopes::add);

    return authorizedScopes;
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
    return authorizationState.getAuthorizationScopes(
        ownerType, ownerId, resourceType, permissionType);
  }

  private Stream<AuthorizationScope> getAuthorizedScopes(
      final Map<String, Object> authorizationClaims,
      final EntityType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var authorizationOwnerType =
        switch (ownerType) {
          case GROUP -> AuthorizationOwnerType.GROUP;
          case ROLE -> AuthorizationOwnerType.ROLE;
          case USER -> AuthorizationOwnerType.USER;
          case MAPPING_RULE -> AuthorizationOwnerType.MAPPING_RULE;
          case CLIENT -> AuthorizationOwnerType.CLIENT;
          case UNSPECIFIED -> AuthorizationOwnerType.UNSPECIFIED;
        };

    final var direct =
        getDirectAuthorizedAuthorizationScopes(
            authorizationOwnerType, ownerId, resourceType, permissionType)
            .stream();
    final var viaRole =
        membershipState.getMemberships(ownerType, ownerId, RelationType.ROLE).stream()
            .flatMap(
                roleId ->
                    getDirectAuthorizedAuthorizationScopes(
                        AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                        .stream());
    final var viaGroups =
        fetchGroups(authorizationClaims, ownerType, ownerId).stream()
            .<AuthorizationScope>mapMulti(
                (groupId, stream) -> {
                  getDirectAuthorizedAuthorizationScopes(
                          AuthorizationOwnerType.GROUP, groupId, resourceType, permissionType)
                      .forEach(stream);
                  membershipState
                      .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                      .stream()
                      .flatMap(
                          roleId ->
                              getDirectAuthorizedAuthorizationScopes(
                                  AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                                  .stream())
                      .forEach(stream);
                });
    return Stream.concat(direct, Stream.concat(viaRole, viaGroups));
  }

  private List<String> fetchGroups(
      final Map<String, Object> authorizations, final EntityType ownerType, final String ownerId) {
    final List<String> groupsClaims =
        (List<String>) authorizations.get(Authorization.USER_GROUPS_CLAIMS);
    if (groupsClaims != null) {
      return groupsClaims;
    }
    return membershipState.getMemberships(ownerType, ownerId, RelationType.GROUP);
  }

  /**
   * Checks if a user is assigned to a specific tenant. If multi-tenancy is disabled, this method
   * will always return true. If a command is written by Zeebe internally, it will also always
   * return true.
   *
   * @param command The command send by the user
   * @param tenantId The tenant we want to check assignment for
   * @return true if assigned, false otherwise
   */
  public boolean isAssignedToTenant(final TypedRecord<?> command, final String tenantId) {
    return isAssignedToTenant(
        AuthorizationRequest.builder().command(command).tenantId(tenantId).build());
  }

  public boolean isAssignedToTenant(final AuthorizationRequestMetadata request) {
    if (!multiTenancyEnabled) {
      return true;
    }
    return getAuthorizedTenantIds(request.claims()).isAuthorizedForTenantId(request.tenantId());
  }

  public AuthorizedTenants getAuthorizedTenantIds(final TypedRecord<?> command) {
    return getAuthorizedTenantIds(command.getAuthorizations());
  }

  private AuthorizedTenants getAuthorizedTenantIds(final Map<String, Object> authorizations) {
    if (isAuthorizedAnonymousUser(authorizations)) {
      return AuthorizedTenants.ANONYMOUS;
    }

    if (!multiTenancyEnabled) {
      return AuthorizedTenants.DEFAULT_TENANTS;
    }

    final var authorizedTenants = new HashSet<String>();
    getUsername(authorizations)
        .ifPresent(
            username ->
                authorizedTenants.addAll(
                    getAuthorizedTenantIds(authorizations, EntityType.USER, username)
                        .collect(Collectors.toSet())));

    getClientId(authorizations)
        .ifPresent(
            clientId ->
                authorizedTenants.addAll(
                    getAuthorizedTenantIds(authorizations, EntityType.CLIENT, clientId)
                        .collect(Collectors.toSet())));

    final var tenantsOfMappingRule =
        getPersistedMappingRules(authorizations)
            .flatMap(
                mappingRule ->
                    getAuthorizedTenantIds(
                        authorizations, EntityType.MAPPING_RULE, mappingRule.getMappingRuleId()))
            .collect(Collectors.toSet());
    authorizedTenants.addAll(tenantsOfMappingRule);

    return new AuthenticatedAuthorizedTenants(authorizedTenants);
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final AuthorizationRequestMetadata request) {
    return getPersistedMappingRules(request.claims());
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final Map<String, Object> authorizations) {
    final var claims =
        (Map<String, Object>)
            authorizations.getOrDefault(Authorization.USER_TOKEN_CLAIMS, Map.of());
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), claims);
  }

  public void clearAuthorizationsCache() {
    authorizationsCache.invalidateAll();
  }

  public record AuthorizationRequestMetadata(
      Map<String, Object> claims,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      boolean isNewResource,
      boolean isTenantOwnedResource,
      String tenantId,
      Set<AuthorizationScope> authorizationScopes) {

    public String getForbiddenErrorMessage() {
      final var authorizationScopesContainsOnlyWildcard =
          authorizationScopes.size() == 1
              && authorizationScopes.contains(AuthorizationScope.WILDCARD);
      return authorizationScopesContainsOnlyWildcard
          ? FORBIDDEN_ERROR_MESSAGE.formatted(permissionType, resourceType)
          : FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              permissionType,
              resourceType,
              authorizationScopes.stream()
                  .filter(
                      scope -> scope.getResourceId() != null && !scope.getResourceId().isEmpty())
                  .map(AuthorizationScope::getResourceId)
                  .sorted()
                  .toList());
    }

    public String getTenantErrorMessage() {
      final var errorMsg =
          isNewResource ? FORBIDDEN_FOR_TENANT_ERROR_MESSAGE : NOT_FOUND_FOR_TENANT_ERROR_MESSAGE;
      return errorMsg.formatted(permissionType, resourceType, tenantId);
    }
  }

  public static class AuthorizationRequest {
    private TypedRecord<?> command;
    private Map<String, Object> authorizationClaims;
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private final Set<AuthorizationScope> authorizationScopes;
    private String tenantId;
    private boolean isNewResource;
    private boolean isTenantOwnedResource;

    public AuthorizationRequest() {
      authorizationScopes = new HashSet<>();
      authorizationScopes.add(AuthorizationScope.WILDCARD);
      tenantId = null;
      isNewResource = false;
      isTenantOwnedResource = true;
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId,
        final boolean isNewResource,
        final boolean isTenantOwnedResource) {
      this.command = command;
      this.resourceType = resourceType;
      this.permissionType = permissionType;
      authorizationScopes = new HashSet<>();
      authorizationScopes.add(AuthorizationScope.WILDCARD);
      this.tenantId = tenantId;
      this.isNewResource = isNewResource;
      this.isTenantOwnedResource = isTenantOwnedResource;
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId,
        final boolean isNewResource) {
      this(command, resourceType, permissionType, tenantId, isNewResource, true);
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId) {
      this(command, resourceType, permissionType, tenantId, false, true);
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType) {
      this(command, resourceType, permissionType, null, false, false);
    }

    public AuthorizationRequest command(final TypedRecord<?> command) {
      this.command = command;
      return this;
    }

    public AuthorizationRequest authorizationClaims(final Map<String, Object> authorizationClaims) {
      this.authorizationClaims = authorizationClaims;
      return this;
    }

    public AuthorizationRequest resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public AuthorizationRequest permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public AuthorizationRequest tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public AuthorizationRequest isNewResource(final boolean isNewResource) {
      this.isNewResource = isNewResource;
      return this;
    }

    public AuthorizationRequest isTenantOwnedResource(final boolean isTenantOwnedResource) {
      this.isTenantOwnedResource = isTenantOwnedResource;
      return this;
    }

    public AuthorizationRequest addAuthorizationScope(final AuthorizationScope authorizationScope) {
      authorizationScopes.add(authorizationScope);
      return this;
    }

    public AuthorizationRequest addResourceId(final String resourceId) {
      authorizationScopes.add(AuthorizationScope.of(resourceId));
      return this;
    }

    public AuthorizationRequestMetadata build() {
      if (command != null) {
        authorizationClaims = command.getAuthorizations();
        return new AuthorizationRequestMetadata(
            command.getAuthorizations(),
            resourceType,
            permissionType,
            isNewResource,
            isTenantOwnedResource,
            tenantId,
            Collections.unmodifiableSet(authorizationScopes));
      }
      return new AuthorizationRequestMetadata(
          authorizationClaims,
          resourceType,
          permissionType,
          isNewResource,
          isTenantOwnedResource,
          tenantId,
          Collections.unmodifiableSet(authorizationScopes));
    }

    public static AuthorizationRequest builder() {
      return new AuthorizationRequest();
    }
  }

  public static class ForbiddenException extends RuntimeException {

    public ForbiddenException(final AuthorizationRequest authRequest) {
      this(authRequest.build());
    }

    public ForbiddenException(final AuthorizationRequestMetadata authRequest) {
      super(authRequest.getForbiddenErrorMessage());
    }

    public RejectionType getRejectionType() {
      return RejectionType.FORBIDDEN;
    }
  }

  public static class NotFoundException extends RuntimeException {

    public NotFoundException(final String message) {
      super(message);
    }
  }

  /**
   * This exception is thrown when the authorization and tenant caches can't find data in the state
   * for a given key. This must be a checked exception, because of the way the {@link LoadingCache}
   * works.
   */
  private static final class AuthorizationsNotFoundException extends Exception {}

  // Helper record for authorization results
  private record AuthorizationResult(boolean hasTenantAccess, boolean hasResourceAccess) {
    public boolean hasBothAccess() {
      return hasTenantAccess && hasResourceAccess;
    }
  }

  private record AuthorizationRejection(
      Rejection rejection, AuthorizationRejectionType authorizationRejectionType) {}

  private enum AuthorizationRejectionType {
    TENANT,
    PERMISSION
  }
}
