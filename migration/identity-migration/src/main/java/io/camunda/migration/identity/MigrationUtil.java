/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Group;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MigrationUtil {

  public static List<CreateAuthorizationRequest> extractCombinedPermissions(
      final List<CreateAuthorizationRequest> allPermissions) {
    // Key used to group permission sets
    record PermissionKey(
        String ownerId,
        AuthorizationOwnerType ownerType,
        String resourceId,
        AuthorizationResourceType resourceType) {}

    final Map<PermissionKey, Set<PermissionType>> permissionMap = new HashMap<>();

    for (final CreateAuthorizationRequest request : allPermissions) {
      final PermissionKey key =
          new PermissionKey(
              request.ownerId(), request.ownerType(), request.resourceId(), request.resourceType());

      permissionMap.merge(
          key,
          new HashSet<>(request.permissionTypes()),
          (existing, incoming) -> {
            existing.addAll(incoming);
            return existing;
          });
    }

    return permissionMap.entrySet().stream()
        .map(
            entry ->
                new CreateAuthorizationRequest(
                    entry.getKey().ownerId(),
                    entry.getKey().ownerType(),
                    entry.getKey().resourceId(),
                    entry.getKey().resourceType(),
                    entry.getValue()))
        .toList();
  }

  // Normalizes the group ID to ensure it meets the requirements for a valid group ID.
  // For SaaS the group ID is derived from the group name, because in the old identity
  // management system the group ID was generated internally.
  public static String normalizeGroupID(final Group group) {
    if (group.name() == null || group.name().isEmpty()) {
      return group.id();
    }
    final String groupName = group.name();

    return normalizeID(groupName);
  }

  public static String normalizeID(final String entityId) {
    String normalizedId =
        entityId.toLowerCase().replaceAll("[^a-z0-9_@.-]", "_"); // Replace disallowed characters

    if (normalizedId.length() > 256) {
      normalizedId = normalizedId.substring(0, 256);
    }
    return normalizedId;
  }

  public static Set<PermissionType> convertDecisionPermissions(final Set<String> permissions) {
    final Set<PermissionType> result = new HashSet<>();
    if (permissions.contains("READ")) {
      result.add(PermissionType.READ_DECISION_DEFINITION);
      result.add(PermissionType.READ_DECISION_INSTANCE);
    }
    if (permissions.contains("DELETE")) {
      result.add(PermissionType.DELETE_DECISION_INSTANCE);
    }
    return result;
  }

  public static Set<PermissionType> convertProcessPermissions(final Set<String> permissions) {
    final Set<PermissionType> result = new HashSet<>();
    if (permissions.contains("READ")) {
      result.add(PermissionType.READ_PROCESS_DEFINITION);
      result.add(PermissionType.READ_PROCESS_INSTANCE);
    }
    if (permissions.contains("UPDATE_PROCESS_INSTANCE")
        || permissions.contains("DELETE")
        || permissions.contains("DELETE_PROCESS_INSTANCE")) {
      result.add(PermissionType.UPDATE_PROCESS_INSTANCE);
    }
    if (permissions.contains("START_PROCESS_INSTANCE")) {
      result.add(PermissionType.CREATE_PROCESS_INSTANCE);
    }
    return result;
  }
}
