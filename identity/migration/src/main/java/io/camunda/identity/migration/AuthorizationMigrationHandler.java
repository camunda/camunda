/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import io.camunda.identity.migration.dto.UserResourceAuthorization;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationMigrationHandler {

  private final AuthorizationServices<AuthorizationRecord> authorizationService;
  private final ManagementIdentityProxy managementIdentityProxy;

  public AuthorizationMigrationHandler(
      final AuthorizationServices<AuthorizationRecord> authorizationService,
      final ManagementIdentityProxy managementIdentityProxy) {
    this.authorizationService = authorizationService;
    this.managementIdentityProxy = managementIdentityProxy;
  }

  public void migrate() {
    UserResourceAuthorization lastRecord = null;
    while (true) {
      final List<UserResourceAuthorization> authorizations =
          managementIdentityProxy.fetchUserResourceAuthorizations(lastRecord, 100);
      if (authorizations.isEmpty()) {
        return;
      }
      lastRecord = authorizations.getLast();
      final Map<String, Map<String, Map<String, List<String>>>> authorizationMap =
          authorizations.stream()
              .collect(
                  Collectors.groupingBy(
                      UserResourceAuthorization::username,
                      Collectors.groupingBy(
                          UserResourceAuthorization::resourceType,
                          Collectors.groupingBy(
                              UserResourceAuthorization::permission,
                              Collectors.mapping(
                                  UserResourceAuthorization::resourceId, Collectors.toList())))));

      authorizationMap.forEach(
          (owner, value) -> {
            value.forEach(
                (resourceType, permissionAndResources) -> {
                  final Map<PermissionType, List<String>> permissions =
                      permissionAndResources.entrySet().stream()
                          .flatMap(
                              e ->
                                  convertPermission(e).stream()
                                      .map(entry -> new SimpleEntry<>(entry, e.getValue())))
                          .collect(
                              Collectors.groupingBy(
                                  SimpleEntry::getKey,
                                  Collectors.flatMapping(
                                      e -> e.getValue().stream(), Collectors.toList())));
                  final long ownerKey = getOwnerKeyForUsername(owner);
                  authorizationService.patchAuthorization(
                      new PatchAuthorizationRequest(
                          ownerKey,
                          PermissionAction.ADD,
                          convertResourceType(resourceType),
                          permissions));

                  final Collection<UserResourceAuthorization> migrated =
                      permissionAndResources.entrySet().stream()
                          .flatMap(
                              entry ->
                                  entry.getValue().stream()
                                      .map(
                                          resourceId ->
                                              new UserResourceAuthorization(
                                                  owner, resourceId, resourceType, entry.getKey())))
                          .toList();
                  managementIdentityProxy.markAsMigrated(migrated);
                });
          });
    }
  }

  private static AuthorizationResourceType convertResourceType(final String resourceType) {
    if ("process-definition".equalsIgnoreCase(resourceType)) {
      return AuthorizationResourceType.PROCESS_DEFINITION;
    }
    if ("decision-definition".equalsIgnoreCase(resourceType)) {
      return AuthorizationResourceType.DECISION_DEFINITION;
    }
    throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
  }

  private static List<PermissionType> convertPermission(final Entry<String, List<String>> e) {
    if (e.getKey().startsWith("read")) {
      return List.of(PermissionType.READ);
    }
    if (e.getKey().startsWith("write")) {
      return List.of(PermissionType.CREATE, PermissionType.UPDATE, PermissionType.DELETE);
    }

    if (e.getKey().startsWith("create")) {
      return List.of(PermissionType.CREATE);
    }

    if (e.getKey().startsWith("update")) {
      return List.of(PermissionType.UPDATE);
    }

    if (e.getKey().startsWith("delete")) {
      return List.of(PermissionType.DELETE);
    }

    throw new IllegalArgumentException("Unsupported permission type: " + e.getKey());
  }

  private long getOwnerKeyForUsername(final String owner) {
    // TODO should finalize the code to return the id for mapping rule
    return 0;
  }
}
