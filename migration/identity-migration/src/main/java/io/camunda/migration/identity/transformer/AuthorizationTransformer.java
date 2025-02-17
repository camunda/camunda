/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.transformer;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AuthorizationTransformer {

  static final List<AuthorizationResourceType> RESOURCE_TYPES =
      Arrays.asList(
          AuthorizationResourceType.PROCESS_DEFINITION,
          AuthorizationResourceType.DECISION_DEFINITION,
          AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION);

  // TODO: this part needs to be revisited
  //  public static List<PatchAuthorizationRequest> transform(
  //      final long ownerKey, final List<Permission> oldPermissions) {
  //    final var groupedPermissions =
  //        oldPermissions.stream()
  //            .flatMap(AuthorizationTransformer::transformToAuthorizations)
  //            .collect(
  //                Collectors.groupingBy(
  //                    ResourceTypePermissionTypeResourceId::resourceType,
  //                    Collectors.groupingBy(
  //                        ResourceTypePermissionTypeResourceId::permissionType,
  //                        Collectors.mapping(
  //                            ResourceTypePermissionTypeResourceId::resourceId,
  //                            Collectors.toSet()))));
  //    return groupedPermissions.entrySet().stream()
  //        .map(entry -> createPatchAuthorizationRequest(ownerKey, entry))
  //        .toList();
  //  }
  //
  //  private static PatchAuthorizationRequest createPatchAuthorizationRequest(
  //      final long ownerKey,
  //      final Entry<AuthorizationResourceType, Map<PermissionType, Set<String>>> entry) {
  //    return new PatchAuthorizationRequest(
  //        ownerKey, PermissionAction.ADD, entry.getKey(), entry.getValue());
  //  }
  //
  //  private static Stream<ResourceTypePermissionTypeResourceId> transformToAuthorizations(
  //      final Permission permission) {
  //
  //    final List<String> applications = List.of("operate", "tasklist");
  //    return applications.stream()
  //        .filter(a -> permission.apiName().toLowerCase().contains(a))
  //        .flatMap(
  //            a -> {
  //              final var applicationAccess =
  //                  Stream.of(
  //                      new ResourceTypePermissionTypeResourceId(
  //                          AuthorizationResourceType.APPLICATION, PermissionType.ACCESS, a));
  //              if (permission.definition().toLowerCase().contains("read")) {
  //                return Stream.concat(applicationAccess, transformReadPermissions());
  //              } else if (permission.definition().toLowerCase().contains("write")) {
  //                return Stream.concat(applicationAccess, transformWritePermissions());
  //              } else {
  //                return Stream.empty();
  //              }
  //            });
  //  }

  private static Stream<ResourceTypePermissionTypeResourceId> transformWritePermissions() {
    return RESOURCE_TYPES.stream()
        .flatMap(
            resourceType ->
                Arrays.stream(PermissionType.values())
                    .map(pt -> new ResourceTypePermissionTypeResourceId(resourceType, pt, "*")));
  }

  private static Stream<ResourceTypePermissionTypeResourceId> transformReadPermissions() {
    return RESOURCE_TYPES.stream()
        .map(
            resourceType ->
                new ResourceTypePermissionTypeResourceId(resourceType, PermissionType.READ, "*"));
  }

  private record ResourceTypePermissionTypeResourceId(
      AuthorizationResourceType resourceType, PermissionType permissionType, String resourceId) {}
}
