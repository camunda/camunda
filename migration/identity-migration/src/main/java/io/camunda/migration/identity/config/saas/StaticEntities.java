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

  public static final Set<String> ROLE_IDS =
      Set.of("developer", "operationsengineer", "taskuser", "visitor");

  public static final List<CreateRoleRequest> ROLES =
      List.of(
          new CreateRoleRequest("developer", "Developer", ""),
          new CreateRoleRequest("operationsengineer", "Operations Engineer", ""),
          new CreateRoleRequest("taskuser", "Task User", ""),
          new CreateRoleRequest("visitor", "Visitor", ""));

  public static final List<CreateAuthorizationRequest> ROLE_PERMISSIONS =
      List.of(
          // DEVELOPER
          new CreateAuthorizationRequest(
              "developer",
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "developer",
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "developer",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              AuthorizationResourceType.PROCESS_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "developer",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              AuthorizationResourceType.DECISION_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "developer",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION
                  .getSupportedPermissionTypes()),
          // OPERATIONS ENGINEER
          new CreateAuthorizationRequest(
              "operationsengineer",
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "operationsengineer",
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
              "operationsengineer",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              AuthorizationResourceType.DECISION_DEFINITION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "operationsengineer",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION
                  .getSupportedPermissionTypes()),
          // TASK USER
          new CreateAuthorizationRequest(
              "taskuser",
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "taskuser",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION,
                  PermissionType.READ_USER_TASK,
                  PermissionType.UPDATE_USER_TASK,
                  PermissionType.CREATE_PROCESS_INSTANCE)),
          // TODO: add document permissions once implemented
          // VISITOR
          new CreateAuthorizationRequest(
              "visitor",
              AuthorizationOwnerType.ROLE,
              "operate",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "visitor",
              AuthorizationOwnerType.ROLE,
              "tasklist",
              AuthorizationResourceType.APPLICATION,
              AuthorizationResourceType.APPLICATION.getSupportedPermissionTypes()),
          new CreateAuthorizationRequest(
              "visitor",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(
                  PermissionType.READ_PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  PermissionType.READ_USER_TASK)),
          new CreateAuthorizationRequest(
              "visitor",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_DEFINITION,
              Set.of(
                  PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE)),
          new CreateAuthorizationRequest(
              "visitor",
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
              Set.of(PermissionType.READ))
          // TODO: add document permissions once implemented
          );
}
