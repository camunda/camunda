/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AuthorizationCheckBehavior {

  public static final String FORBIDDEN_ERROR_MESSAGE =
      "Insufficient permissions to perform operation '%s' on resource '%s'";
  public static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE =
      FORBIDDEN_ERROR_MESSAGE + ", required resource identifiers are one of '%s'";
  public static final String NOT_FOUND_ERROR_MESSAGE =
      "Expected to %s with key '%s', but no %s was found";
  public static final String WILDCARD_PERMISSION = "*";
  private static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  private final AuthorizationState authorizationState;
  private final UserState userState;
  private final SecurityConfiguration securityConfig;
  private final MappingState mappingState;
  private final GroupState groupState;

  public AuthorizationCheckBehavior(
      final ProcessingState processingState, final SecurityConfiguration securityConfig) {
    authorizationState = processingState.getAuthorizationState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
    groupState = processingState.getGroupState();
    processingState.getTenantState();
    this.securityConfig = securityConfig;
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
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return Either.right(null);
    }

    if (!request.getCommand().hasRequestMetadata()) {
      // The command is written by Zeebe internally. Internal Zeebe commands are always authorized
      return Either.right(null);
    }

    if (isAuthorizedAnonymousUser(request.getCommand())) {
      return Either.right(null);
    }

    final Stream<String> authorizedResourceIdentifiers;
    final var userKey = getUserKey(request);
    if (userKey.isPresent()) {
      final var userOptional = userState.getUser(userKey.get());
      if (userOptional.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.UNAUTHORIZED,
                UNAUTHORIZED_ERROR_MESSAGE.formatted(
                    request.getPermissionType(), request.getResourceType())));
      }
      // verify if the user is authorized for the tenant
      if (!isUserAuthorizedForTenant(request, userOptional.get())) {
        return Either.left(
            new Rejection(RejectionType.NOT_FOUND, request.getForbiddenErrorMessage()));
      }

      authorizedResourceIdentifiers =
          getUserAuthorizedResourceIdentifiers(
              userOptional.get(), request.getResourceType(), request.getPermissionType());
    } else {
      authorizedResourceIdentifiers = getMappingsAuthorizedResourceIdentifiers(request);
    }

    // Check if authorizations contain a resource identifier that matches the required resource
    // identifiers
    if (hasRequiredPermission(request.getResourceIds(), authorizedResourceIdentifiers)) {
      return Either.right(null);
    } else {
      return Either.left(
          new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage()));
    }
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

  private Optional<Long> getUserKey(final AuthorizationRequest request) {
    return getUserKey(request.getCommand());
  }

  private boolean isUserAuthorizedForTenant(
      final AuthorizationRequest request, final PersistedUser user) {
    final var tenantId = request.tenantId;
    if (tenantId.equals(TenantOwned.DEFAULT_TENANT_IDENTIFIER)) {
      return true;
    }

    if (user.getTenantIdsList().contains(tenantId)) {
      return true;
    }

    return areGroupsAuthorizedForTenant(user.getGroupKeysList(), tenantId);
  }

  private boolean isMappingAuthorizedForTenant(
      final AuthorizationRequest request, final PersistedMapping mapping) {
    final var tenantId = request.tenantId;
    if (tenantId.equals(TenantOwned.DEFAULT_TENANT_IDENTIFIER)) {
      return true;
    }

    if (mapping.getTenantIdsList().contains(request.getTenantId())) {
      return true;
    }

    return areGroupsAuthorizedForTenant(mapping.getGroupKeysList(), tenantId);
  }

  private boolean areGroupsAuthorizedForTenant(final List<Long> groupKeys, final String tenantId) {
    return getTenantIdsForGroups(groupKeys).contains(tenantId);
  }

  private Set<String> getTenantIdsForGroups(final List<Long> groupKeys) {
    return groupKeys.stream()
        .map(groupState::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(group -> group.getTenantIdsList().stream())
        .collect(Collectors.toSet());
  }

  public Set<String> getAllAuthorizedResourceIdentifiers(final AuthorizationRequest request) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return Set.of(WILDCARD_PERMISSION);
    }

    if (isAuthorizedAnonymousUser(request.getCommand())) {
      return Set.of(WILDCARD_PERMISSION);
    }

    return getUserKey(request)
        .map(
            userKey ->
                userState
                    .getUser(userKey)
                    .map(
                        persistedUser ->
                            getUserAuthorizedResourceIdentifiers(
                                persistedUser,
                                request.getResourceType(),
                                request.getPermissionType()))
                    .orElseGet(Stream::empty))
        .orElseGet(() -> getMappingsAuthorizedResourceIdentifiers(request))
        .collect(Collectors.toSet());
  }

  /**
   * Get direct authorized resource identifiers for a given owner, resource type and permission
   * type. This does not include inherited authorizations, for example authorizations for users from
   * assigned roles or groups.
   */
  public Set<String> getDirectAuthorizedResourceIdentifiers(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return authorizationState.getResourceIdentifiers(
        ownerType, ownerId, resourceType, permissionType);
  }

  // TODO: refactor role and group keys to use groupNames and roleNames after
  // https://github.com/camunda/camunda/issues/26981
  private Stream<String> getUserAuthorizedResourceIdentifiers(
      final PersistedUser user,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    // Get resource identifiers for this user
    final var userAuthorizedResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            AuthorizationOwnerType.USER, user.getUsername(), resourceType, permissionType);
    // Get resource identifiers for the user's roles
    final var roleAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwnerKeys(
            AuthorizationOwnerType.ROLE, user.getRoleKeysList(), resourceType, permissionType);
    // Get resource identifiers for the user's groups
    final var groupAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwnerKeys(
            AuthorizationOwnerType.GROUP, user.getGroupKeysList(), resourceType, permissionType);
    return Stream.concat(
        userAuthorizedResourceIdentifiers.stream(),
        Stream.concat(roleAuthorizedResourceIdentifiers, groupAuthorizedResourceIdentifiers));
  }

  // TODO: refactor to use String-based ownerKeys when all Identity-related entities use them
  // https://github.com/camunda/camunda/issues/26981
  private Stream<String> getMappingsAuthorizedResourceIdentifiers(
      final AuthorizationRequest request) {
    return extractUserTokenClaims(request.getCommand())
        .<PersistedMapping>mapMulti(
            (claim, stream) ->
                mappingState.get(claim.claimName(), claim.claimValue()).ifPresent(stream))
        .filter(mapping -> isMappingAuthorizedForTenant(request, mapping))
        .<String>mapMulti(
            (mapping, stream) -> {
              getAuthorizedResourceIdentifiersForOwners(
                      AuthorizationOwnerType.MAPPING,
                      List.of(mapping.getId()),
                      request.getResourceType(),
                      request.getPermissionType())
                  .forEach(stream);
              getAuthorizedResourceIdentifiersForOwnerKeys(
                      AuthorizationOwnerType.GROUP,
                      mapping.getGroupKeysList(),
                      request.getResourceType(),
                      request.getPermissionType())
                  .forEach(stream);
              getAuthorizedResourceIdentifiersForOwnerKeys(
                      AuthorizationOwnerType.ROLE,
                      mapping.getRoleKeysList(),
                      request.getResourceType(),
                      request.getPermissionType())
                  .forEach(stream);
            });
  }

  // TODO: refactor to use String-based ownerKeys when all Identity-related entities use them
  // https://github.com/camunda/camunda/issues/26981
  private Stream<String> getAuthorizedResourceIdentifiersForOwnerKeys(
      final AuthorizationOwnerType ownerType,
      final List<Long> ownerKeys,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var ownerIds = ownerKeys.stream().map(String::valueOf).toList();
    return getAuthorizedResourceIdentifiersForOwners(
        ownerType, ownerIds, resourceType, permissionType);
  }

  private Stream<String> getAuthorizedResourceIdentifiersForOwners(
      final AuthorizationOwnerType ownerType,
      final List<String> ownerIds,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return ownerIds.stream()
        .flatMap(
            ownerId ->
                authorizationState
                    .getResourceIdentifiers(ownerType, ownerId, resourceType, permissionType)
                    .stream());
  }

  private static boolean hasRequiredPermission(
      final Set<String> requiredResourceIdentifiers,
      final Stream<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.anyMatch(requiredResourceIdentifiers::contains);
  }

  public AuthorizedTenants getAuthorizedTenantIds(final TypedRecord<?> command) {
    if (isAuthorizedAnonymousUser(command)) {
      return AuthorizedTenants.ANONYMOUS;
    }

    // todo: this is a temporary solution until we adjust all the tests to fetch the tenant from the
    // state
    if (command.getAuthorizations().get(Authorization.AUTHORIZED_TENANTS) != null) {
      final var authorizedTenants =
          (List<String>) command.getAuthorizations().get(Authorization.AUTHORIZED_TENANTS);
      return new AuthenticatedAuthorizedTenants(authorizedTenants);
    }

    final var userKey = getUserKey(command);
    if (userKey.isPresent()) {
      return userState
          .getUser(userKey.get())
          .map(
              user -> {
                final List<String> tenantIds = user.getTenantIdsList();
                tenantIds.addAll(getTenantIdsForGroups(user.getGroupKeysList()));
                return tenantIds;
              })
          .filter(t -> !t.isEmpty())
          .<AuthorizedTenants>map(AuthenticatedAuthorizedTenants::new)
          .orElse(AuthorizedTenants.DEFAULT_TENANTS);
    }

    final var tenantsOfMapping =
        extractUserTokenClaims(command)
            .map(claim -> mappingState.get(claim.claimName(), claim.claimValue()))
            .<PersistedMapping>mapMulti(Optional::ifPresent)
            .flatMap(
                mapping -> {
                  final var tenantIdsList = mapping.getTenantIdsList();
                  tenantIdsList.addAll(getTenantIdsForGroups(mapping.getGroupKeysList()));
                  return tenantIdsList.stream();
                })
            .toList();
    return tenantsOfMapping.isEmpty()
        ? AuthorizedTenants.DEFAULT_TENANTS
        : new AuthenticatedAuthorizedTenants(tenantsOfMapping);
  }

  private Optional<Long> getUserKey(final TypedRecord<?> command) {
    return Optional.ofNullable(
            (String) command.getAuthorizations().get(Authorization.AUTHORIZED_USERNAME))
        .flatMap(userState::getUser)
        .map(PersistedUser::getUserKey);
  }

  private static Stream<UserTokenClaim> extractUserTokenClaims(final TypedRecord<?> command) {
    return command.getAuthorizations().entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(Authorization.USER_TOKEN_CLAIM_PREFIX))
        .flatMap(
            claimEntry -> {
              final var claimName =
                  claimEntry.getKey().substring(Authorization.USER_TOKEN_CLAIM_PREFIX.length());
              final var claimValue = claimEntry.getValue();
              if (claimValue instanceof final Collection<?> collection) {
                return collection.stream()
                    .map(value -> new UserTokenClaim(claimName, value.toString()));
              } else {
                return Stream.of(new UserTokenClaim(claimName, claimValue.toString()));
              }
            });
  }

  public static final class AuthorizationRequest {
    private final TypedRecord<?> command;
    private final AuthorizationResourceType resourceType;
    private final PermissionType permissionType;
    private final Set<String> resourceIds;
    private final String tenantId;

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId) {
      this.command = command;
      this.resourceType = resourceType;
      this.permissionType = permissionType;
      resourceIds = new HashSet<>();
      resourceIds.add(WILDCARD_PERMISSION);
      this.tenantId = tenantId;
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType) {
      this(command, resourceType, permissionType, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
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

    public AuthorizationRequest addResourceId(final String resourceId) {
      resourceIds.add(resourceId);
      return this;
    }

    public Set<String> getResourceIds() {
      return resourceIds;
    }

    public String getTenantId() {
      return tenantId;
    }

    public String getForbiddenErrorMessage() {
      final var resourceIdsContainsOnlyWildcard =
          resourceIds.size() == 1 && resourceIds.contains(WILDCARD_PERMISSION);
      return resourceIdsContainsOnlyWildcard
          ? FORBIDDEN_ERROR_MESSAGE.formatted(permissionType, resourceType)
          : FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              permissionType, resourceType, resourceIds.stream().sorted().toList());
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

  private record UserTokenClaim(String claimName, String claimValue) {}
}
