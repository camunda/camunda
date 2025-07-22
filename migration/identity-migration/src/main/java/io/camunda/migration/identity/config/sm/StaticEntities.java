/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceIdFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StaticEntities {

  private static final String IDENTITY_RESOURCE_ID = "identity";
  private static final String OPERATE_RESOURCE_ID = "operate";
  private static final String TASKLIST_RESOURCE_ID = "tasklist";

  public static List<CreateAuthorizationRequest> getAuthorizationsByAudience(
      final String audience, final String ownerId, final AuthorizationOwnerType ownerType) {
    final var authorizations =
        Map.of(
            "camunda-identity-resource-server:read",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.AUTHORIZATION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.TENANT,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.GROUP,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.ROLE,
                    Set.of(PermissionType.READ))),
            "camunda-identity-resource-server:read:users",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.ROLE,
                    Set.of(PermissionType.READ))),
            "camunda-identity-resource-server:write",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.AUTHORIZATION,
                    AuthorizationResourceType.AUTHORIZATION.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    IDENTITY_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.TENANT,
                    AuthorizationResourceType.TENANT.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.GROUP,
                    AuthorizationResourceType.GROUP.getSupportedPermissionTypes()),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.USER,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.ROLE,
                    AuthorizationResourceType.ROLE.getSupportedPermissionTypes())),
            "operate-api:read:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.BATCH_OPERATION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    OPERATE_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_PROCESS_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE))),
            "operate-api:write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.BATCH_OPERATION,
                    Set.of(PermissionType.READ, PermissionType.CREATE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    OPERATE_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
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
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE,
                        PermissionType.CREATE_DECISION_INSTANCE,
                        PermissionType.DELETE_DECISION_INSTANCE))),
            "tasklist-api:read:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    TASKLIST_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(PermissionType.READ_PROCESS_DEFINITION, PermissionType.READ_USER_TASK))),
            "tasklist-api:write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ID,
                    TASKLIST_RESOURCE_ID,
                    AuthorizationResourceType.APPLICATION,
                    Set.of(PermissionType.ACCESS)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.RESOURCE,
                    Set.of(PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    Set.of(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_USER_TASK,
                        PermissionType.UPDATE_USER_TASK))),
            "zeebe-api:write:*",
            List.of(
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.MESSAGE,
                    Set.of(PermissionType.CREATE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.SYSTEM,
                    Set.of(PermissionType.UPDATE, PermissionType.READ)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
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
                    ResourceIdFormat.ANY,
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
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_DEFINITION,
                    Set.of(
                        PermissionType.CREATE_DECISION_INSTANCE,
                        PermissionType.DELETE_DECISION_INSTANCE)),
                new CreateAuthorizationRequest(
                    ownerId,
                    ownerType,
                    ResourceIdFormat.ANY,
                    "*",
                    AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                    Set.of(PermissionType.UPDATE, PermissionType.DELETE))));

    return authorizations.getOrDefault(audience, List.of());
  }
}
