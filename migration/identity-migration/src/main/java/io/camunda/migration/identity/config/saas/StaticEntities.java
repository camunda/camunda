/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.saas;

import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;

public class StaticEntities {

  public static final String DEVELOPER_ROLE_ID = "developer";
  public static final String OPERATIONS_ENGINEER_ROLE_ID = "operationsengineer";
  public static final String TASK_USER_ROLE_ID = "taskuser";
  public static final String VISITOR_ROLE_ID = "visitor";

  public static final String IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE = "process-definition";
  public static final String IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE = "decision-definition";

  public static final Set<String> ROLE_IDS =
      Set.of(DEVELOPER_ROLE_ID, OPERATIONS_ENGINEER_ROLE_ID, TASK_USER_ROLE_ID, VISITOR_ROLE_ID);

  public static final List<CreateRoleRequest> ROLES =
      List.of(
          new CreateRoleRequest(DEVELOPER_ROLE_ID, "Developer", ""),
          new CreateRoleRequest(OPERATIONS_ENGINEER_ROLE_ID, "Operations Engineer", ""),
          new CreateRoleRequest(TASK_USER_ROLE_ID, "Task User", ""),
          new CreateRoleRequest(VISITOR_ROLE_ID, "Visitor", ""));

  public static final List<CreateAuthorizationRequest> ROLE_PERMISSIONS =
      List.of(
          // DEVELOPER
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              AuthorizationResourceType.PROCESS_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              AuthorizationResourceType.DECISION_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION
                  .getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.RESOURCE,
              AuthorizationResourceType.RESOURCE.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              DEVELOPER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.BATCH,
              Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
          // OPERATIONS ENGINEER
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  PermissionType.UPDATE_PROCESS_INSTANCE,
                  PermissionType.CREATE_PROCESS_INSTANCE,
                  PermissionType.DELETE_PROCESS_INSTANCE)),
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              AuthorizationResourceType.DECISION_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION
                  .getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.RESOURCE,
              Set.of(
                  PermissionType.READ,
                  PermissionType.DELETE_RESOURCE,
                  PermissionType.DELETE_PROCESS,
                  PermissionType.DELETE_DRD,
                  PermissionType.DELETE_FORM)),
          new CreateAuthorizationRequest(
              OPERATIONS_ENGINEER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.BATCH,
              Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
          // TASK USER
          new CreateAuthorizationRequest(
              TASK_USER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              TASK_USER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION,
                  PermissionType.READ_USER_TASK,
                  PermissionType.UPDATE_USER_TASK,
                  PermissionType.CREATE_PROCESS_INSTANCE)),
          // VISITOR
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.COMPONENT,
              AuthorizationResourceType.COMPONENT.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  PermissionType.READ_USER_TASK)),
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              Set.of(
                  PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE)),
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              Set.of(PermissionType.READ)),
          new CreateAuthorizationRequest(
              VISITOR_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.BATCH,
              Set.of(PermissionType.READ)));

  public static List<CreateAuthorizationRequest> getZeebeClientPermissions(final String clientId) {
    return List.of(
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.MESSAGE,
            Set.of(PermissionType.CREATE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.SYSTEM,
            AuthorizationResourceType.SYSTEM.getSupportedPermissionTypes()),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.RESOURCE,
            Set.of(
                PermissionType.CREATE,
                PermissionType.DELETE_FORM,
                PermissionType.DELETE_PROCESS,
                PermissionType.DELETE_DRD,
                PermissionType.DELETE_RESOURCE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.PROCESS_DEFINITION,
            Set.of(
                PermissionType.UPDATE_PROCESS_INSTANCE,
                PermissionType.UPDATE_USER_TASK,
                PermissionType.CREATE_PROCESS_INSTANCE,
                PermissionType.DELETE_PROCESS_INSTANCE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.DECISION_DEFINITION,
            Set.of(
                PermissionType.CREATE_DECISION_INSTANCE, PermissionType.DELETE_DECISION_INSTANCE)));
  }

  public static List<CreateAuthorizationRequest> getOperateClientPermissions(
      final String clientId) {
    return List.of(
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.MESSAGE,
            Set.of(PermissionType.READ)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.BATCH,
            Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.RESOURCE,
            Set.of(PermissionType.READ, PermissionType.DELETE_PROCESS, PermissionType.DELETE_DRD)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.PROCESS_DEFINITION,
            Set.of(
                PermissionType.READ_PROCESS_DEFINITION,
                PermissionType.READ_PROCESS_INSTANCE,
                PermissionType.DELETE_PROCESS_INSTANCE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.DECISION_DEFINITION,
            Set.of(PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE)),
        new CreateAuthorizationRequest(
            clientId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
            Set.of(PermissionType.READ)));
  }

  public static List<CreateAuthorizationRequest> getTasklistClientPermissions(
      final String ownerId) {
    return List.of(
        new CreateAuthorizationRequest(
            ownerId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.RESOURCE,
            Set.of(PermissionType.READ)),
        new CreateAuthorizationRequest(
            ownerId,
            AuthorizationOwnerType.CLIENT,
            "*",
            AuthorizationResourceType.PROCESS_DEFINITION,
            Set.of(
                PermissionType.READ_PROCESS_DEFINITION,
                PermissionType.READ_USER_TASK,
                PermissionType.UPDATE_USER_TASK)));
  }
}
