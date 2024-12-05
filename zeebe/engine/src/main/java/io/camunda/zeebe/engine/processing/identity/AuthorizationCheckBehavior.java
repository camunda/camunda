/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AuthorizationCheckBehavior {

  public static final String UNAUTHORIZED_ERROR_MESSAGE =
      "Unauthorized to perform operation '%s' on resource '%s'";
  public static final String UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE =
      UNAUTHORIZED_ERROR_MESSAGE + " with %s";
  public static final String NOT_FOUND_ERROR_MESSAGE =
      "Expected to %s for tenant '%s', but no tenant found";
  public static final String WILDCARD_PERMISSION = "*";
  private final AuthorizationState authorizationState;
  private final UserState userState;
  private final SecurityConfiguration securityConfig;

  public AuthorizationCheckBehavior(
      final AuthorizationState authorizationState,
      final UserState userState,
      final SecurityConfiguration securityConfig) {
    this.authorizationState = authorizationState;
    this.userState = userState;
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
  public Either<RejectionType, Void> isAuthorized(final AuthorizationRequest request) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return Either.right(null);
    }

    if (!request.getCommand().hasRequestMetadata()) {
      // The command is written by Zeebe internally. Internal Zeebe commands are always authorized
      return Either.right(null);
    }

    final Stream<String> authorizedResourceIdentifiers;
    final var userKey = getUserKey(request);
    if (userKey.isPresent()) {
      final var userOptional = userState.getUser(userKey.get());
      if (userOptional.isEmpty()) {
        return Either.left(RejectionType.UNAUTHORIZED);
      }
      // verify if the user is authorized for the tenant
      if (!isUserAuthorizedForTenant(request, userOptional.get())) {
        return Either.left(RejectionType.NOT_FOUND);
      }

      authorizedResourceIdentifiers =
          getUserAuthorizedResourceIdentifiers(
              userOptional.get(), request.getResourceType(), request.getPermissionType());
    } else {
      authorizedResourceIdentifiers = Stream.empty();
    }

    // Check if authorizations contain a resource identifier that matches the required resource
    // identifiers
    if (hasRequiredPermission(request.getResourceIds(), authorizedResourceIdentifiers)) {
      return Either.right(null);
    } else {
      return Either.left(RejectionType.UNAUTHORIZED);
    }
  }

  private static Optional<Long> getUserKey(final AuthorizationRequest request) {
    return getUserKey(request.getCommand());
  }

  private boolean isUserAuthorizedForTenant(
      final AuthorizationRequest request, final PersistedUser user) {
    final var tenantId = request.tenantId;
    if (tenantId.equals(TenantOwned.DEFAULT_TENANT_IDENTIFIER)) {
      return true;
    }

    return user.getTenantIdsList().contains(tenantId);
  }

  public Set<String> getAllAuthorizedResourceIdentifiers(final AuthorizationRequest request) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return Set.of(WILDCARD_PERMISSION);
    }

    return getUserKey(request)
        .map(
            userKey -> {
              final var userOptional = userState.getUser(userKey);
              if (userOptional.isEmpty()) {
      return new HashSet<>();
    }
    return getUserAuthorizedResourceIdentifiers(
        userOptional.get(), request.getResourceType(), request.getPermissionType());
            })
        .orElseGet(Stream::empty)
        .collect(Collectors.toSet());
  }

  /**
   * Get direct authorized resource identifiers for a given owner, resource type and permission
   * type. This does not include inherited authorizations, for example authorizations for users from
   * assigned roles or groups.
   */
  public Set<String> getDirectAuthorizedResourceIdentifiers(
      final long ownerKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType);
  }

  /**
   * Get all authorized resource identifiers for a given owner, resource type and permission type.
   * This includes direct authorizations and inherited authorizations, for example authorizations
   * for users from assigned roles or groups.
   */
  public Set<String> getAllAuthorizedResourceIdentifiers(
      final long ownerKey,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return switch (ownerType) {
      case USER ->
          {
        final var userOptional = userState.getUser(ownerKey);
        if (userOptional.isEmpty()) {
          yield new HashSet<>();
        }
        yield getUserAuthorizedResourceIdentifiers(
            userOptional.get(), resourceType, permissionType)
              .collect(Collectors.toSet());
      }
      case ROLE, GROUP ->
          // Roles and groups can only have direct authorizations
          getDirectAuthorizedResourceIdentifiers(ownerKey, resourceType, permissionType);
      // TODO add MAPPING
      default -> new HashSet<>();
    };
  }

  private Stream<String> getUserAuthorizedResourceIdentifiers(
      final PersistedUser user,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    // Get resource identifiers for this user
    final var userAuthorizedResourceIdentifiers =
        authorizationState.getResourceIdentifiers(user.getUserKey(), resourceType, permissionType);
    // Get resource identifiers for the user's roles
    final var roleAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwners(
            user.getRoleKeysList(), resourceType, permissionType);
    // Get resource identifiers for the user's groups
    final var groupAuthorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiersForOwners(
            user.getGroupKeysList(), resourceType, permissionType);
    return Stream.concat(
        userAuthorizedResourceIdentifiers.stream(),
        Stream.concat(roleAuthorizedResourceIdentifiers, groupAuthorizedResourceIdentifiers));
  }

  private Stream<String> getAuthorizedResourceIdentifiersForOwners(
      final List<Long> ownerKeys,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return ownerKeys.stream()
        .flatMap(
            ownerKey ->
                authorizationState
                    .getResourceIdentifiers(ownerKey, resourceType, permissionType)
                    .stream());
  }

  private boolean hasRequiredPermission(
      final Set<String> requiredResourceIdentifiers,
      final Stream<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.anyMatch(requiredResourceIdentifiers::contains);
  }

  public List<String> getAuthorizedTenantIds(final TypedRecord<?> command) {
    // todo: this is a temporary solution until we adjust all the tests to fetch the tenant from the
    // state
    if (command.getAuthorizations().get(Authorization.AUTHORIZED_TENANTS) != null) {
      return (List<String>) command.getAuthorizations().get(Authorization.AUTHORIZED_TENANTS);
    }
    final var userKey = getUserKey(command);
    final List<String> authorizedTenantIds = new ArrayList<>();
    authorizedTenantIds.add(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return userKey
        .map(
            key ->
                userState
                    .getUser(key)
                    .map(PersistedUser::getTenantIdsList)
                    .orElse(authorizedTenantIds))
        .orElse(authorizedTenantIds);
  }

  private static Optional<Long> getUserKey(final TypedRecord<?> command) {
    return Optional.ofNullable(
        (Long) command.getAuthorizations().get(Authorization.AUTHORIZED_USER_KEY));
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
  }

  public static class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(
        final AuthorizationRequest authRequest, final String resourceMessage) {
      super(
          UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              authRequest.getPermissionType(), authRequest.getResourceType(), resourceMessage));
    }
  }

  public static class NotFoundException extends RuntimeException {

    public NotFoundException(final AuthorizationRequest authRequest, final String resourceMessage) {
      super(NOT_FOUND_ERROR_MESSAGE.formatted(resourceMessage, authRequest.getTenantId()));
    }
  }
}
