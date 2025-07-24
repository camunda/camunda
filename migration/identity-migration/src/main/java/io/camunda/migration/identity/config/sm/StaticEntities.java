/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import io.camunda.migration.identity.config.sm.OidcProperties.Audiences;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticEntities {

  private static final String IDENTITY_RESOURCE_ID = "identity";
  private static final String OPERATE_RESOURCE_ID = "operate";
  private static final String TASKLIST_RESOURCE_ID = "tasklist";

  public static List<CreateAuthorizationRequest> getAuthorizationsByAudience(
      final Audiences audiences,
      final String permission,
      final String ownerId,
      final AuthorizationOwnerType ownerType) {
    final var authorizations =
        Map.of(
            audiences.getIdentity() + ":read",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.AUTHORIZATION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.TENANT,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.GROUP,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.ROLE,
                    Set.of(PermissionType.READ))),
            audiences.getIdentity() + ":read:users",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.ROLE,
                    Set.of(PermissionType.READ))),
            audiences.getIdentity() + ":write",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.AUTHORIZATION,
                    AuthorizationResourceType.AUTHORIZATION.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.TENANT,
                    AuthorizationResourceType.TENANT.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.GROUP,
                    AuthorizationResourceType.GROUP.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.ROLE,
                    AuthorizationResourceType.ROLE.getSupportedPermissionTypes())),
            audiences.getOperate() + ":read:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.BATCH_OPERATION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    OPERATE_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_PROCESS_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE))),
            audiences.getOperate() + ":write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.BATCH_OPERATION,
                    Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    OPERATE_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_PROCESS_INSTANCE,
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        PermissionType.DELETE_PROCESS_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE,
                        PermissionType.CREATE_DECISION_INSTANCE,
                        PermissionType.DELETE_DECISION_INSTANCE))),
            audiences.getTasklist() + ":read:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    TASKLIST_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(PermissionType.READ_PROCESS_DEFINITION, PermissionType.READ_USER_TASK))),
            audiences.getTasklist() + ":write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    TASKLIST_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_USER_TASK,
                        PermissionType.UPDATE_USER_TASK))),
            audiences.getZeebe() + ":write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.CREATE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.SYSTEM,
                    Set.of(PermissionType.UPDATE, PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(
                        PermissionType.CREATE,
                        PermissionType.DELETE_FORM,
                        PermissionType.DELETE_PROCESS,
                        PermissionType.DELETE_DRD,
                        PermissionType.DELETE_RESOURCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        PermissionType.UPDATE_USER_TASK,
                        PermissionType.CREATE_PROCESS_INSTANCE,
                        PermissionType.DELETE_PROCESS_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.CREATE_DECISION_INSTANCE,
                        PermissionType.DELETE_DECISION_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.UPDATE, PermissionType.DELETE))));

    return authorizations.getOrDefault(permission, List.of());
  }
}
