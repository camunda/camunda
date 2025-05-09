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
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
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
  private final AuthorizationState authorizationState;
  private final MappingState mappingState;
  private final MembershipState membershipState;

  private final boolean authorizationsEnabled;
  private final boolean multiTenancyEnabled;

  public AuthorizationCheckBehavior(
      final ProcessingState processingState, final SecurityConfiguration securityConfig) {
    authorizationState = processingState.getAuthorizationState();
    mappingState = processingState.getMappingState();
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
    final var applicationId = getApplicationId(request);

    if (username.isPresent()) {
      final var userAuthorized =
          isEntityAuthorized(request, EntityType.USER, Set.of(username.get()));
      if (userAuthorized.isRight()) {
        return userAuthorized;
      }
    } else if (applicationId.isPresent()) {
      final var applicationAuthorized =
          isEntityAuthorized(request, EntityType.APPLICATION, Set.of(applicationId.get()));
      if (applicationAuthorized.isRight()) {
        return applicationAuthorized;
      }
    }

    return isEntityAuthorized(
        request,
        EntityType.MAPPING,
        getPersistedMappings(request)
            .map(PersistedMapping::getMappingId)
            .collect(Collectors.toSet()));
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
  private Either<Rejection, Void> isEntityAuthorized(
      final AuthorizationRequest request,
      final EntityType entityType,
      final Collection<String> entityIds) {
    if (multiTenancyEnabled) {
      final var isAssignedToTenant =
          entityIds.stream()
              .noneMatch(
                  entityId ->
                      getAuthorizedTenantIds(entityType, entityId)
                          .anyMatch(request.tenantId::equals));
      if (isAssignedToTenant) {
        final var rejectionType =
            request.isNewResource() ? RejectionType.FORBIDDEN : RejectionType.NOT_FOUND;
        return Either.left(new Rejection(rejectionType, request.getTenantErrorMessage()));
      }
    }

    if (!authorizationsEnabled) {
      return Either.right(null);
    }

    final var isAuthorizedForResource =
        entityIds.stream()
            .flatMap(
                entityId ->
                    getAuthorizedResourceIdentifiers(
                        entityType,
                        entityId,
                        request.getResourceType(),
                        request.getPermissionType()))
            .anyMatch(resourceId -> request.getResourceIds().contains(resourceId));
    if (isAuthorizedForResource) {
      return Either.right(null);
    }

    return Either.left(new Rejection(RejectionType.FORBIDDEN, request.getForbiddenErrorMessage()));
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

  private Optional<String> getApplicationId(final AuthorizationRequest request) {
    return getApplicationId(request.getCommand());
  }

  private Optional<String> getApplicationId(final TypedRecord<?> command) {
    return Optional.ofNullable(
        (String) command.getAuthorizations().get(Authorization.AUTHORIZED_APPLICATION_ID));
  }

  private Stream<String> getAuthorizedTenantIds(
      final EntityType entityType, final String entityId) {
    return Stream.concat(
        membershipState.getMemberships(entityType, entityId, RelationType.TENANT).stream(),
        Stream.concat(
            membershipState.getMemberships(entityType, entityId, RelationType.GROUP).stream()
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

  public Set<String> getAllAuthorizedResourceIdentifiers(final AuthorizationRequest request) {
    if (!authorizationsEnabled || isAuthorizedAnonymousUser(request.getCommand())) {
      return Set.of(WILDCARD_PERMISSION);
    }

    final var authorizedResourceIds = new HashSet<String>();

    final var optionalUsername = getUsername(request);
    if (optionalUsername.isPresent()) {
      getAuthorizedResourceIdentifiers(
              EntityType.USER,
              optionalUsername.get(),
              request.getResourceType(),
              request.getPermissionType())
          .forEach(authorizedResourceIds::add);
    }
    // If a username was present, don't use the application id
    else {
      getApplicationId(request)
          .map(
              applicationId ->
                  getAuthorizedResourceIdentifiers(
                      EntityType.APPLICATION,
                      applicationId,
                      request.getResourceType(),
                      request.getPermissionType()))
          .ifPresent(
              idsForApplicationId -> idsForApplicationId.forEach(authorizedResourceIds::add));
    }

    // mappings can layer on top of username/application id
    getPersistedMappings(request)
        .flatMap(
            mapping ->
                getAuthorizedResourceIdentifiers(
                    EntityType.MAPPING,
                    mapping.getMappingId(),
                    request.getResourceType(),
                    request.getPermissionType()))
        .forEach(authorizedResourceIds::add);

    return authorizedResourceIds;
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

  private Stream<String> getAuthorizedResourceIdentifiers(
      final EntityType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var authorizationOwnerType =
        switch (ownerType) {
          case GROUP -> AuthorizationOwnerType.GROUP;
          case ROLE -> AuthorizationOwnerType.ROLE;
          case USER -> AuthorizationOwnerType.USER;
          case MAPPING -> AuthorizationOwnerType.MAPPING;
          case APPLICATION -> AuthorizationOwnerType.APPLICATION;
          case UNSPECIFIED -> AuthorizationOwnerType.UNSPECIFIED;
        };

    final var direct =
        getDirectAuthorizedResourceIdentifiers(
            authorizationOwnerType, ownerId, resourceType, permissionType)
            .stream();
    final var viaRole =
        membershipState.getMemberships(ownerType, ownerId, RelationType.ROLE).stream()
            .flatMap(
                roleId ->
                    getDirectAuthorizedResourceIdentifiers(
                        AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                        .stream());
    final var viaGroups =
        membershipState.getMemberships(ownerType, ownerId, RelationType.GROUP).stream()
            .<String>mapMulti(
                (groupId, stream) -> {
                  getDirectAuthorizedResourceIdentifiers(
                          AuthorizationOwnerType.GROUP, groupId, resourceType, permissionType)
                      .forEach(stream);
                  membershipState
                      .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                      .stream()
                      .flatMap(
                          roleId ->
                              getDirectAuthorizedResourceIdentifiers(
                                  AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                                  .stream())
                      .forEach(stream);
                });
    return Stream.concat(direct, Stream.concat(viaRole, viaGroups));
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
      final var tenantIds = getAuthorizedTenantIds(EntityType.USER, username.get()).toList();
      if (tenantIds.isEmpty()) {
        return AuthorizedTenants.DEFAULT_TENANTS;
      } else {
        return new AuthenticatedAuthorizedTenants(tenantIds);
      }
    }

    final var applicationId = getApplicationId(command);
    if (applicationId.isPresent()) {
      final var tenantIds =
          getAuthorizedTenantIds(EntityType.APPLICATION, applicationId.get()).toList();
      if (tenantIds.isEmpty()) {
        return AuthorizedTenants.DEFAULT_TENANTS;
      } else {
        return new AuthenticatedAuthorizedTenants(tenantIds);
      }
    }

    final var tenantsOfMapping =
        getPersistedMappings(command)
            .flatMap(mapping -> getAuthorizedTenantIds(EntityType.MAPPING, mapping.getMappingId()))
            .collect(Collectors.toSet());

    return tenantsOfMapping.isEmpty()
        ? AuthorizedTenants.DEFAULT_TENANTS
        : new AuthenticatedAuthorizedTenants(tenantsOfMapping);
  }

  private Stream<PersistedMapping> getPersistedMappings(final AuthorizationRequest request) {
    return getPersistedMappings(request.getCommand());
  }

  private Stream<PersistedMapping> getPersistedMappings(final TypedRecord<?> command) {
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
        .mapMulti(Optional::ifPresent);
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
