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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Set;

public final class AuthorizationCheckBehavior {

  private static final String WILDCARD_PERMISSION = "*";
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
   * <p>This method does not take a Map of resource identifiers to check for. The user is considered
   * authorized if it is the default user, or if it has a wildcard permission for the provided
   * resource type and permission type. If you want to check for specific resource identifiers, use
   * {@link #isAuthorized(TypedRecord, AuthorizationRequest, Set)}
   *
   * @param command the command to check authorization for
   * @param authorizationRequest the authorization request to check authorization for. This contains
   *     the resource type and permission type
   * @return true if the user is authorized, false otherwise
   */
  public <T extends UnifiedRecordValue> boolean isAuthorized(
      final TypedRecord<T> command, final AuthorizationRequest authorizationRequest) {
    return isAuthorized(command, authorizationRequest, Set.of());
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource. The user key is taken from *
   * the authorizations of the command.
   *
   * <p>The caller of this method should provide a Map of resource identifiers. Examples of this
   * are:
   *
   * <ul>
   *   <li>Key: bpmnProcessId, Value: myProcess
   *   <li>Key: processInstanceKey, Value: 1234567890
   * </ul>
   *
   * @param command the command to check authorization for
   * @param authorizationRequest the authorization request to check authorization for. This contains
   *     the resource type and permission type
   * @param requiredResourceIdentifiers the resource identifiers to check for
   * @return true if the user is authorized, false otherwise
   */
  public <T extends UnifiedRecordValue> boolean isAuthorized(
      final TypedRecord<T> command,
      final AuthorizationRequest authorizationRequest,
      final Set<String> requiredResourceIdentifiers) {
    if (!engineConfig.isEnableAuthorization()) {
      return true;
    }

    final var userKey = (Long) command.getAuthorizations().get(Authorization.AUTHORIZED_USER_KEY);
    if (userKey == null) {
      return false;
    }
    return isAuthorized(userKey, authorizationRequest, requiredResourceIdentifiers);
  }

  private boolean isAuthorized(
      final long userKey,
      final AuthorizationRequest authorizationRequest,
      final Set<String> requiredResourceIdentifiers) {
    final var userOptional = userState.getUser(userKey);
    if (userOptional.isEmpty()) {
      return false;
    }
    final var user = userOptional.get();

    // The default user has all permissions
    if (user.getUserType().equals(UserType.DEFAULT)) {
      return true;
    }

    final var authorizedResourceIdentifiers =
        getAuthorizedResourceIdentifiers(
            userKey, authorizationRequest.resourceType(), authorizationRequest.permissionType());

    // Check if authorizations contain a resource identifier that matches the required resource
    // identifiers
    return hasWildcardPermission(authorizedResourceIdentifiers)
        || hasRequiredPermission(requiredResourceIdentifiers, authorizedResourceIdentifiers);
  }

  private Set<String> getAuthorizedResourceIdentifiers(
      final long userKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    // Get resource identifiers for this user, resource type and permission type from state
    return authorizationState.getResourceIdentifiers(userKey, resourceType, permissionType);
  }

  private boolean hasWildcardPermission(final Set<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.stream().anyMatch(WILDCARD_PERMISSION::equals);
  }

  private boolean hasRequiredPermission(
      final Set<String> requiredResourceIdentifiers,
      final Set<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.stream().anyMatch(requiredResourceIdentifiers::contains);
  }

  public record AuthorizationRequest(
      AuthorizationResourceType resourceType, PermissionType permissionType) {}
}
