/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Utility class for building common authorization requests used across user task command
 * processors.
 */
final class UserTaskAuthorizationHelper {

  private UserTaskAuthorizationHelper() {
    // Utility class, prevent instantiation
  }

  /**
   * Builds an authorization request for PROCESS_DEFINITION permission.
   *
   * @param command the user task command
   * @param persistedUserTask the persisted user task record
   * @param permissionType the permission type (e.g., UPDATE_USER_TASK, COMPLETE_USER_TASK)
   * @return the authorization request
   */
  static AuthorizationRequest buildProcessDefinitionRequest(
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedUserTask,
      final PermissionType permissionType) {
    return AuthorizationRequest.builder()
        .command(command)
        .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .permissionType(permissionType)
        .tenantId(persistedUserTask.getTenantId())
        .addResourceId(persistedUserTask.getBpmnProcessId())
        .build();
  }

  /**
   * Builds an authorization request for USER_TASK permission with all standard properties.
   *
   * @param command the user task command
   * @param persistedUserTask the persisted user task record
   * @param permissionType the permission type (e.g., UPDATE, CLAIM, COMPLETE)
   * @return the authorization request
   */
  static AuthorizationRequest buildUserTaskRequest(
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedUserTask,
      final PermissionType permissionType) {
    return buildUserTaskRequest(
        command, persistedUserTask, permissionType, buildUserTaskProperties(persistedUserTask));
  }

  /**
   * Builds an authorization request for USER_TASK permission with custom properties.
   *
   * @param command the user task command
   * @param persistedUserTask the persisted user task record
   * @param permissionType the permission type (e.g., UPDATE, CLAIM, COMPLETE)
   * @param properties the user task authorization properties
   * @return the authorization request
   */
  static AuthorizationRequest buildUserTaskRequest(
      final TypedRecord<UserTaskRecord> command,
      final UserTaskRecord persistedUserTask,
      final PermissionType permissionType,
      final UserTaskAuthorizationProperties properties) {
    return AuthorizationRequest.builder()
        .command(command)
        .resourceType(AuthorizationResourceType.USER_TASK)
        .permissionType(permissionType)
        .tenantId(persistedUserTask.getTenantId())
        .addResourceId(String.valueOf(persistedUserTask.getUserTaskKey()))
        .resourceProperties(properties)
        .build();
  }

  /**
   * Builds the standard user task properties containing assignee, candidate users, and candidate
   * groups.
   *
   * @param userTask the persisted user task record
   * @return the user task authorization properties
   */
  private static UserTaskAuthorizationProperties buildUserTaskProperties(
      final UserTaskRecord userTask) {
    return UserTaskAuthorizationProperties.builder()
        .assignee(userTask.getAssignee())
        .candidateUsers(userTask.getCandidateUsersList())
        .candidateGroups(userTask.getCandidateGroupsList())
        .build();
  }
}
