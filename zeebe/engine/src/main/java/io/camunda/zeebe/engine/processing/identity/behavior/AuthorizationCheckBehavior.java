/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.behavior;

import io.camunda.zeebe.engine.state.authorization.ResourceIdentifiers;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.Map;

public final class AuthorizationCheckBehavior {

  public static final String DELIMITER = ":";
  private static final String WILDCARD_PERMISSION = "*";
  private final AuthorizationState authorizationState;
  private final UserState userState;

  public AuthorizationCheckBehavior(
      final AuthorizationState authorizationState, final UserState userState) {
    this.authorizationState = authorizationState;
    this.userState = userState;
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource
   *
   * <p>This method does not take a Map of resource identifiers to check for. The user is considered
   * authorized if it is the default user, or if it has a wildcard permission for the provided
   * resource type and permission type. If you want to check for specific resource identifiers, use
   * {@link #isAuthorized(TypedRecord, AuthorizationResourceType, PermissionType, Map)}
   *
   * @param command the command to check authorization for
   * @param resourceType the type of resource to check authorization for
   * @param permissionType the type of permission to check authorization for (CRUD)
   * @return true if the user is authorized, false otherwise
   */
  public boolean isAuthorized(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return isAuthorized(command, resourceType, permissionType, Map.of());
  }

  /**
   * Checks if a user is Authorized to perform an action on a resource
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
   * @param resourceType the type of resource to check authorization for
   * @param permissionType the type of permission to check authorization for (CRUD)
   * @param requiredResourceIdentifiers the resource identifiers to check for
   * @return true if the user is authorized, false otherwise
   */
  public boolean isAuthorized(
      final TypedRecord<?> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Map<String, String> requiredResourceIdentifiers) {

    // TODO return true if authorization is disabled.

    // TODO what if no userkey on authorizations
    final var userKey = (Long) command.getAuthorizations().get("userKey");
    final var user = userState.getUserByKey(userKey);
    if (user == null) {
      return false;
    }

    // The default user has all permissions
    // Commented out so I can do manual testing with this user
    if (user.getUserType().equals(UserType.DEFAULT)) {
      return true;
    }

    // Get resource identifiers for this user, resource type and permission type from state
    final var authorizedResourceIdentifiers =
        getResourceIdentifiersForUser(resourceType, permissionType, userKey);

    // Check if authorizations contain a resource identifier that matches the required resource
    // identifiers
    return hasWildcardPermission(authorizedResourceIdentifiers)
        || hasRequiredPermission(requiredResourceIdentifiers, authorizedResourceIdentifiers);
  }

  private List<String> getResourceIdentifiersForUser(
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Long userKey) {
    return authorizationState
        .getResourceIdentifiers(userKey, resourceType, permissionType)
        // If no permissions were added for the user, getting the resource identifiers returns an
        // empty optional.
        .orElseGet(ResourceIdentifiers::new)
        .getResourceIdentifiers();
  }

  private static boolean hasWildcardPermission(final List<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.stream().anyMatch(WILDCARD_PERMISSION::equals);
  }

  private static boolean hasRequiredPermission(
      final Map<String, String> requiredResourceIdentifiers,
      final List<String> authorizedResourceIdentifiers) {
    return authorizedResourceIdentifiers.stream()
        .filter(resourceId -> resourceId.contains(DELIMITER))
        .map(
            resourceId -> {
              final var splitResourceId = resourceId.split(DELIMITER, 2);
              return new ResourceIdentifier(splitResourceId[0], splitResourceId[1]);
            })
        .anyMatch(
            resourceId ->
                requiredResourceIdentifiers
                    .get(resourceId.resourceName)
                    .equals(resourceId.resourceValue));
  }

  private record ResourceIdentifier(String resourceName, String resourceValue) {}
}
