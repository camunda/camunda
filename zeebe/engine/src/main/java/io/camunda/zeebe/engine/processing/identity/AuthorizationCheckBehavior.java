/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
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
  public static final String WILDCARD_PERMISSION = "*";
  private final AuthorizationState authorizationState;
  private final UserState userState;
  private final EngineConfiguration engineConfig;

  public AuthorizationCheckBehavior(
      final AuthorizationState authorizationState,
      final UserState userState,
      final EngineConfiguration engineConfig) {
    this.authorizationState = authorizationState;
    this.userState = userState;
    this.engineConfig = engineConfig;
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource. The user key is taken from
   * the authorizations of the command.
   *
   * <p>The caller of this method should provide an {@link AuthorizationRequest}. This object
   * contains the data required to do the check.
   *
   * @param request the authorization request to check authorization for. This contains the command,
   *     the resource type, the permission type and a set of resource identifiers
   * @return true if the entity is authorized, false otherwise
   */
  public boolean isAuthorized(final AuthorizationRequest request) {
    if (!engineConfig.isEnableAuthorization()) {
      return true;
    }

    if (!request.getCommand().hasRequestMetadata()) {
      // The command is written by Zeebe internally. Internal Zeebe commands are always authorized
      return true;
    }

    Set<String> authorizedResourceIdentifiers = new HashSet<>();

    final var userKey = getUserKey(request);
    if (userKey.isPresent()) {
      authorizedResourceIdentifiers =
          getUserAuthorizedResourceIdentifiers(
              userKey.get(), request.getResourceType(), request.getPermissionType());
    }

    // Check if authorizations contain a resource identifier that matches the required resource
    // identifiers
    return hasRequiredPermission(request.getResourceIds(), authorizedResourceIdentifiers);
  }

  private static Optional<Long> getUserKey(final AuthorizationRequest request) {
    return Optional.ofNullable(
        (Long) request.getCommand().getAuthorizations().get(Authorization.AUTHORIZED_USER_KEY));
  }

  public Set<String> getAuthorizedResourceIdentifiers(final AuthorizationRequest request) {
    if (!engineConfig.isEnableAuthorization()) {
      return Set.of(WILDCARD_PERMISSION);
    }

    final var userKey = getUserKey(request);
    if (userKey.isEmpty()) {
      return new HashSet<>();
    }

    return getUserAuthorizedResourceIdentifiers(
        userKey.get(), request.getResourceType(), request.getPermissionType());
  }

  public Set<String> getAuthorizedResourceIdentifiers(
      final long ownerKey,
      final AuthorizationOwnerType ownerType,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return switch (ownerType) {
      case USER -> getUserAuthorizedResourceIdentifiers(ownerKey, resourceType, permissionType);
      case ROLE ->
          getRoleAuthorizedResourceIdentifiers(List.of(ownerKey), resourceType, permissionType);
      // TODO add MAPPING
      // TODO add GROUP
      default -> new HashSet<>();
    };
  }

  private Set<String> getUserAuthorizedResourceIdentifiers(
      final long userKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var userOptional = userState.getUser(userKey);
    if (userOptional.isEmpty()) {
      return new HashSet<>();
    }
    final var user = userOptional.get();

    // The default user has all permissions
    if (user.getUserType().equals(UserType.DEFAULT)) {
      // TODO this should change when we introduce a default "admin" role to the default user
      return new HashSet<>(Set.of(WILDCARD_PERMISSION));
    }

    // Get resource identifiers for this user
    final var userAuthorizedResourceIdentifiers =
        authorizationState.getResourceIdentifiers(userKey, resourceType, permissionType);
    // Get resource identifiers for the user's roles
    final var roleAuthorizedResourceIdentifiers =
        getRoleAuthorizedResourceIdentifiers(user.getRoleKeysList(), resourceType, permissionType);

    return Stream.concat(
            userAuthorizedResourceIdentifiers.stream(), roleAuthorizedResourceIdentifiers.stream())
        .collect(Collectors.toSet());
  }

  private Set<String> getRoleAuthorizedResourceIdentifiers(
      final List<Long> roleKeys,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return roleKeys.stream()
        .flatMap(
            roleKey ->
                authorizationState
                    .getResourceIdentifiers(roleKey, resourceType, permissionType)
                    .stream())
        .collect(Collectors.toSet());
  }

  private boolean hasRequiredPermission(
      final Set<String> requiredResourceIdentifiers,
      final Set<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.stream().anyMatch(requiredResourceIdentifiers::contains);
  }

  public static final class AuthorizationRequest {
    private final TypedRecord<?> command;
    private final AuthorizationResourceType resourceType;
    private final PermissionType permissionType;
    private final Set<String> resourceIds;

    public AuthorizationRequest(
        final TypedRecord<?> command,
        final AuthorizationResourceType resourceType,
        final PermissionType permissionType) {
      this.command = command;
      this.resourceType = resourceType;
      this.permissionType = permissionType;
      resourceIds = new HashSet<>();
      resourceIds.add(WILDCARD_PERMISSION);
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
  }

  public static class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(
        final AuthorizationRequest authRequest, final String resourceMessage) {
      super(
          UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              authRequest.getPermissionType(), authRequest.getResourceType(), resourceMessage));
    }
  }
}
