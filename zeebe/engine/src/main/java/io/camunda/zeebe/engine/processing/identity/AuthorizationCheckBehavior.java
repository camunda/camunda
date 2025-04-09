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
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMapping;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
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
  public static final String FORBIDDEN_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s' for tenant '%s', but user is not assigned to this tenant";
  public static final String FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE =
      FORBIDDEN_ERROR_MESSAGE + ", required resource identifiers are one of '%s'";
  public static final String NOT_FOUND_ERROR_MESSAGE =
      "Expected to %s with key '%s', but no %s was found";
  public static final String NOT_FOUND_FOR_TENANT_ERROR_MESSAGE =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";
  public static final String WILDCARD_PERMISSION = "*";
  private static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  private final AuthorizationState authorizationState;
  private final UserState userState;
  private final MappingState mappingState;
  private final GroupState groupState;
  private final MembershipState membershipState;

  private final boolean authorizationsEnabled;
  private final boolean multiTenancyEnabled;

  public AuthorizationCheckBehavior(
      final ProcessingState processingState, final SecurityConfiguration securityConfig) {
    authorizationState = processingState.getAuthorizationState();
    userState = processingState.getUserState();
    mappingState = processingState.getMappingState();
    groupState = processingState.getGroupState();
    membershipState = processingState.getMembershipState();
    authorizationsEnabled = securityConfig.getAuthorizations().isEnabled();
    multiTenancyEnabled = securityConfig.getMultiTenancy().isEnabled();
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
    if (!authorizationsEnabled && !multiTenancyEnabled) {
      return Either.right(null);
    }

    if (!request.getCommand().hasRequestMetadata()) {
      // The command is written by Zeebe internally. Internal Zeebe commands are always authorized
      return Either.right(null);
    }

    if (isAuthorizedAnonymousUser(request.getCommand())) {
      return Either.right(null);
    }

    final var username = getUsername(request);

    // TODO: remove this check when username retrieval is supported in JWT modes
    if (username.isPresent()) {
      final var userAuthorized = isUserAuthorized(request, username.get());
      if (userAuthorized.isRight()) {
        return userAuthorized;
      }
    }

    final var mappingAuthorized = isMappingAuthorized(request);
    if (mappingAuthorized.isLeft()) {
      return Either.left(mappingAuthorized.getLeft());
    }

    return Either.right(null);
  }

  /**
   * Verifies a user is authorized to perform this request. This method checks if the user has
   * access to the tenant and if the user has the required permissions for the resource.
   *
   * @param request the authorization request to check authorization for
   * @param username the username of the user making this request
   * @return an {@link Either} containing a {@link Rejection} or {@link Void}
   */
  private Either<Rejection, Void> isUserAuthorized(
      final AuthorizationRequest request, final String username) {
    final var userOptional = userState.getUser(username);
    if (userOptional.isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.UNAUTHORIZED,
              UNAUTHORIZED_ERROR_MESSAGE.formatted(
                  request.getPermissionType(), request.getResourceType())));
    }

    final var user = userOptional.get();
    if (multiTenancyEnabled) {
      if (!isUserAuthorizedForTenant(request, user)) {
        final var rejectionType =
            request.isNewResource() ? RejectionType.FORBIDDEN : RejectionType.NOT_FOUND;
        return Either.left(new Rejection(rejectionType, request.getTenantErrorMessage()));
      }
    }

    if (authorizationsEnabled) {
      final var authorizedResourceIdentifiers =
          getUserAuthorizedResourceIdentifiers(
              user, request.getResourceType(), request.getPermissionType());
      return checkResourceIdentifiers(request, authorizedResourceIdentifiers);
    }
    return Either.right(null);
  }

  /**
   * Verifies a mapping is authorized to perform this request. This method checks if the mapping has
   * the required permissions for the resource.
   *
   * @param request the authorization request to check authorization for
   * @return an {@link Either} containing a {@link Rejection} or {@link Void}
   */
  private Either<Rejection, Void> isMappingAuthorized(final AuthorizationRequest request) {
    final var persistedMappings = getPersistedMappings(request.getCommand());

    if (multiTenancyEnabled) {
      if (!isMappingAuthorizedForTenant(request, persistedMappings)) {
        final var rejectionType =
            request.isNewResource() ? RejectionType.FORBIDDEN : RejectionType.NOT_FOUND;
        return Either.left(new Rejection(rejectionType, request.getTenantErrorMessage()));
      }
    }

    if (authorizationsEnabled) {
      final var authorizedResourceIdentifiers =
          getMappingsAuthorizedResourceIdentifiers(request, persistedMappings);
      return checkResourceIdentifiers(request, authorizedResourceIdentifiers);
    }
    return Either.right(null);
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

  private boolean isUserAuthorizedForTenant(
      final AuthorizationRequest request, final PersistedUser user) {
    final var tenantId = request.tenantId;
    if (user.getTenantIdsList().contains(tenantId)) {
      return true;
    }

    return areGroupsAuthorizedForTenant(user.getGroupIdsList(), tenantId);
  }

  private boolean isMappingAuthorizedForTenant(
      final AuthorizationRequest request, final List<PersistedMapping> persistedMappings) {
    final var tenantId = request.tenantId;

    final var mappingAssignedToTenant =
        persistedMappings.stream()
            .flatMap(persistedMapping -> persistedMapping.getTenantIdsList().stream())
            .collect(Collectors.toSet())
            .contains(tenantId);

    if (mappingAssignedToTenant) {
      return true;
    }

    final var groupKeys =
        persistedMappings.stream()
            .flatMap(persistedMapping -> persistedMapping.getGroupIdsList().stream())
            .collect(Collectors.toSet());

    return areGroupsAuthorizedForTenant(groupKeys, tenantId);
  }

  private boolean areGroupsAuthorizedForTenant(final List<String> groupIds, final String tenantId) {
    return areGroupsAuthorizedForTenant(new HashSet<>(groupIds), tenantId);
  }

  private boolean areGroupsAuthorizedForTenant(final Set<String> groupIds, final String tenantId) {
    return getTenantIdsForGroups(groupIds).contains(tenantId);
  }

  private Set<String> getTenantIdsForGroups(final List<String> groupIds) {
    return getTenantIdsForGroups(new HashSet<>(groupIds));
  }

  private Set<String> getTenantIdsForGroups(final Set<String> groupIds) {
    return groupIds.stream()
        .map(groupState::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .flatMap(group -> group.getTenantIdsList().stream())
        .collect(Collectors.toSet());
  }

  public Set<String> getAllAuthorizedResourceIdentifiers(final AuthorizationRequest request) {
    if (!authorizationsEnabled) {
      return Set.of(WILDCARD_PERMISSION);
    }

    if (isAuthorizedAnonymousUser(request.getCommand())) {
      return Set.of(WILDCARD_PERMISSION);
    }

    return getUsername(request)
        .map(
            username ->
                userState
                    .getUser(username)
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
    final var roleIds =
        membershipState.getMemberships(
            EntityType.USER, Long.toString(user.getUserKey()), RelationType.ROLE);
    final var roleAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwners(
            AuthorizationOwnerType.ROLE, roleIds, resourceType, permissionType);
    // Get resource identifiers for the user's groups
    final var groupAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwners(
            AuthorizationOwnerType.GROUP, user.getGroupIdsList(), resourceType, permissionType);
    return Stream.concat(
        userAuthorizedResourceIdentifiers.stream(),
        Stream.concat(roleAuthorizedResourceIdentifiers, groupAuthorizedResourceIdentifiers));
  }

  private Stream<String> getMappingsAuthorizedResourceIdentifiers(
      final AuthorizationRequest request) {
    return getMappingsAuthorizedResourceIdentifiers(
        request, getPersistedMappings(request.getCommand()));
  }

  // TODO: refactor to use String-based ownerKeys when all Identity-related entities use them
  // https://github.com/camunda/camunda/issues/26981
  private Stream<String> getMappingsAuthorizedResourceIdentifiers(
      final AuthorizationRequest request, final List<PersistedMapping> persistedMappings) {
    return persistedMappings.stream()
        .mapMulti(
            (mapping, stream) -> {
              getAuthorizedResourceIdentifiersForOwners(
                      AuthorizationOwnerType.MAPPING,
                      List.of(mapping.getMappingId()),
                      request.getResourceType(),
                      request.getPermissionType())
                  .forEach(stream);
              getAuthorizedResourceIdentifiersForOwners(
                      AuthorizationOwnerType.GROUP,
                      mapping.getGroupIdsList(),
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

  /**
   * Checks the resource identifiers of the request against the authorized resource identifiers of
   * the entity.
   *
   * @param request the authorization request to check authorization for
   * @param authorizedResourceIdentifiers the authorized resource identifiers of the entity
   * @return an {@link Either} containing a {@link Rejection} if there is no match or {@link Void}
   *     if there is.
   */
  private Either<Rejection, Void> checkResourceIdentifiers(
      final AuthorizationRequest request, final Stream<String> authorizedResourceIdentifiers) {
    final var isAuthorized =
        authorizedResourceIdentifiers.anyMatch(
            resourceId -> request.getResourceIds().contains(resourceId));
    if (isAuthorized) {
      return Either.right(null);
    } else {
      return Either.left(
          new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage()));
    }
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

    final var username = getUsername(command);
    if (username.isPresent()) {
      return userState
          .getUser(username.get())
          .map(
              user -> {
                final List<String> tenantIds = user.getTenantIdsList();
                tenantIds.addAll(getTenantIdsForGroups(user.getGroupIdsList()));
                return tenantIds;
              })
          .filter(t -> !t.isEmpty())
          .<AuthorizedTenants>map(AuthenticatedAuthorizedTenants::new)
          .orElse(AuthorizedTenants.DEFAULT_TENANTS);
    }

    final var tenantsOfMapping =
        getPersistedMappings(command).stream()
            .flatMap(
                mapping -> {
                  final var tenantIdsList = mapping.getTenantIdsList();
                  tenantIdsList.addAll(getTenantIdsForGroups(mapping.getGroupIdsList()));
                  return tenantIdsList.stream();
                })
            .toList();
    return tenantsOfMapping.isEmpty()
        ? AuthorizedTenants.DEFAULT_TENANTS
        : new AuthenticatedAuthorizedTenants(tenantsOfMapping);
  }

  private List<PersistedMapping> getPersistedMappings(final TypedRecord<?> command) {
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
            })
        .map((claim) -> mappingState.get(claim.claimName(), claim.claimValue()))
        .<PersistedMapping>mapMulti(Optional::ifPresent)
        .toList();
  }

  public static final class AuthorizationRequest {
    private final TypedRecord<?> command;
    private final AuthorizationResourceType resourceType;
    private final PermissionType permissionType;
    private final Set<String> resourceIds;
    private final String tenantId;
    private final boolean isNewResource;

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId,
        final boolean isNewResource) {
      this.command = command;
      this.resourceType = resourceType;
      this.permissionType = permissionType;
      resourceIds = new HashSet<>();
      resourceIds.add(WILDCARD_PERMISSION);
      this.tenantId = tenantId;
      this.isNewResource = isNewResource;
    }

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType,
        final String tenantId) {
      this(command, resourceType, permissionType, tenantId, false);
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

    public boolean isNewResource() {
      return isNewResource;
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

  private record UserTokenClaim(String claimName, String claimValue) {}
}
