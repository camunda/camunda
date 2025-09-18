/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;

import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.auth.Authorization;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  public AuthorizationCheckBehavior(
      final ProcessingState processingState, final SecurityConfiguration securityConfig) {
    authorizationState = processingState.getAuthorizationState();
    mappingRuleState = processingState.getMappingRuleState();
    membershipState = processingState.getMembershipState();
    authorizationsEnabled = securityConfig.getAuthorizations().isEnabled();
    multiTenancyEnabled = securityConfig.getMultiTenancy().isChecksEnabled();
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource. The user key is taken from
   * the authorizations of the command.
   *
   * <p>The caller of this method should provide an {@link AuthorizationRequest}. This object
   * contains the data required to do the check.
   *
   * @param request the authorization request to check authorization for. This contains the command,
   *     the resource type, the permission type, a set of resource identifiers and the tenant id
   * @return a {@link Either} containing a {@link RejectionType} if the user is not authorized or
   *     {@link Void} if the user is authorized
   */
  public Either<Rejection, Void> isAuthorized(final AuthorizationRequest request) {

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
  private boolean shouldSkipAuthorization(final AuthorizationRequest request) {
    return (!authorizationsEnabled && !multiTenancyEnabled)
        || (!request.getCommand().hasRequestMetadata()
            && request.getCommand().getBatchOperationReference()
                == batchOperationReferenceNullValue())
        || isAuthorizedAnonymousUser(request.getCommand());
  }

  private AuthorizationResult checkPrimaryAuthorization(
      final AuthorizationRequest request, final List<AuthorizationRejection> aggregatedRejections) {
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

  private boolean hasAccess(
      final Either<AuthorizationRejection, Void> request,
      final List<AuthorizationRejection> aggregatedRejections) {
    if (request.isLeft()) {
      aggregatedRejections.add(request.getLeft());
    }
    return request.isRight();
  }

  public Either<AuthorizationRejection, Void> isTenantAssigned(
      final AuthorizationRequest request, final EntityType entityType, final Set<String> owners) {
    if (multiTenancyEnabled && request.isTenantOwnedResource()) {
      final var notAssignedToTenant =
          owners.stream()
              .noneMatch(
                  entity ->
                      getAuthorizedTenantIds(request.command, entityType, entity)
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
      final AuthorizationRequest request,
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
                        request.command,
                        entityType,
                        entityId,
                        request.getResourceType(),
                        request.getPermissionType()))
            .anyMatch(
                authorizationScope ->
                    request.getAuthorizationScopes().contains(authorizationScope));
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
  private boolean isAuthorizedAnonymousUser(final TypedRecord<?> command) {
    final var authorizationClaims = command.getAuthorizations();
    final var authorizedAnonymousUserClaim =
        authorizationClaims.get(Authorization.AUTHORIZED_ANONYMOUS_USER);
    return Optional.ofNullable(authorizedAnonymousUserClaim).map(Boolean.class::cast).orElse(false);
  }

  private Optional<String> getUsername(final AuthorizationRequest request) {
    return getUsername(request.getCommand());
  }

  private Optional<String> getUsername(final TypedRecord<?> command) {
    return Optional.ofNullable(
        (String) command.getAuthorizations().get(Authorization.AUTHORIZED_USERNAME));
  }

  private Optional<String> getClientId(final AuthorizationRequest request) {
    return getClientId(request.getCommand());
  }

  private Optional<String> getClientId(final TypedRecord<?> command) {
    return Optional.ofNullable(
        (String) command.getAuthorizations().get(Authorization.AUTHORIZED_CLIENT_ID));
  }

  private Stream<String> getAuthorizedTenantIds(
      final TypedRecord<?> command, final EntityType entityType, final String entityId) {
    return Stream.concat(
        membershipState.getMemberships(entityType, entityId, RelationType.TENANT).stream(),
        Stream.concat(
            fetchGroups(command, entityType, entityId).stream()
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
    if (!authorizationsEnabled || isAuthorizedAnonymousUser(request.getCommand())) {
      return Set.of(AuthorizationScope.WILDCARD);
    }

    final var authorizedScopes = new HashSet<AuthorizationScope>();

    final var optionalClientId = getClientId(request);
    if (optionalClientId.isPresent()) {
      getAuthorizedScopes(
              request.command,
              EntityType.CLIENT,
              optionalClientId.get(),
              request.getResourceType(),
              request.getPermissionType())
          .forEach(authorizedScopes::add);
    }
    // If a clientId was present, don't use the username
    else {
      getUsername(request)
          .map(
              username ->
                  getAuthorizedScopes(
                      request.command,
                      EntityType.USER,
                      username,
                      request.getResourceType(),
                      request.getPermissionType()))
          .ifPresent(idsForUsername -> idsForUsername.forEach(authorizedScopes::add));
    }

    // mapping rules can layer on top of username/client id
    getPersistedMappingRules(request)
        .flatMap(
            mappingRule ->
                getAuthorizedScopes(
                    request.command,
                    EntityType.MAPPING_RULE,
                    mappingRule.getMappingRuleId(),
                    request.getResourceType(),
                    request.getPermissionType()))
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
      final TypedRecord<?> command,
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
        fetchGroups(command, ownerType, ownerId).stream()
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
      final TypedRecord<?> command, final EntityType ownerType, final String ownerId) {
    final List<String> groupsClaims =
        (List<String>) command.getAuthorizations().get(Authorization.USER_GROUPS_CLAIMS);
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
    if (!multiTenancyEnabled) {
      return true;
    }

    if (!command.hasRequestMetadata()) {
      // The command is written by Zeebe internally. Internal Zeebe commands are always allowed to
      // access all tenants
      return true;
    }

    return getAuthorizedTenantIds(command).isAuthorizedForTenantId(tenantId);
  }

  public AuthorizedTenants getAuthorizedTenantIds(final TypedRecord<?> command) {
    if (isAuthorizedAnonymousUser(command)) {
      return AuthorizedTenants.ANONYMOUS;
    }

    if (!multiTenancyEnabled) {
      return AuthorizedTenants.DEFAULT_TENANTS;
    }

    final var authorizedTenants = new HashSet<String>();
    getUsername(command)
        .ifPresent(
            username ->
                authorizedTenants.addAll(
                    getAuthorizedTenantIds(command, EntityType.USER, username)
                        .collect(Collectors.toSet())));

    getClientId(command)
        .ifPresent(
            clientId ->
                authorizedTenants.addAll(
                    getAuthorizedTenantIds(command, EntityType.CLIENT, clientId)
                        .collect(Collectors.toSet())));

    final var tenantsOfMappingRule =
        getPersistedMappingRules(command)
            .flatMap(
                mappingRule ->
                    getAuthorizedTenantIds(
                        command, EntityType.MAPPING_RULE, mappingRule.getMappingRuleId()))
            .collect(Collectors.toSet());
    authorizedTenants.addAll(tenantsOfMappingRule);

    return new AuthenticatedAuthorizedTenants(authorizedTenants);
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final AuthorizationRequest request) {
    return getPersistedMappingRules(request.getCommand());
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(final TypedRecord<?> command) {
    final var claims =
        (Map<String, Object>)
            command.getAuthorizations().getOrDefault(Authorization.USER_TOKEN_CLAIMS, Map.of());
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), claims);
  }

  public static final class AuthorizationRequest {
    private final TypedRecord<?> command;
    private final AuthorizationResourceType resourceType;
    private final PermissionType permissionType;
    private final Set<AuthorizationScope> authorizationScopes;
    private final String tenantId;
    private final boolean isNewResource;
    private final boolean isTenantOwnedResource;

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

    public TypedRecord<?> getCommand() {
      return command;
    }

    public AuthorizationResourceType getResourceType() {
      return resourceType;
    }

    public PermissionType getPermissionType() {
      return permissionType;
    }

    public boolean isNewResource() {
      return isNewResource;
    }

    public boolean isTenantOwnedResource() {
      return isTenantOwnedResource;
    }

    public AuthorizationRequest addAuthorizationScope(final AuthorizationScope authorizationScope) {
      authorizationScopes.add(authorizationScope);
      return this;
    }

    public AuthorizationRequest addResourceId(final String resourceId) {
      authorizationScopes.add(AuthorizationScope.of(resourceId));
      return this;
    }

    public Set<AuthorizationScope> getAuthorizationScopes() {
      return authorizationScopes;
    }

    public String getTenantId() {
      return tenantId;
    }

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

  public static class ForbiddenException extends RuntimeException {

    public ForbiddenException(final AuthorizationRequest authRequest) {
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

  // Helper record for authorization results
  private record AuthorizationResult(boolean hasTenantAccess, boolean hasResourceAccess) {
    public boolean hasBothAccess() {
      return hasTenantAccess && hasResourceAccess;
    }
  }

  private record AuthorizationRejection(
      Rejection rejection, AuthorizationRejectionType authorizationRejectionType) {}

  private record UserTokenClaim(String claimName, String claimValue) {}

  private enum AuthorizationRejectionType {
    TENANT,
    PERMISSION
  }
}
