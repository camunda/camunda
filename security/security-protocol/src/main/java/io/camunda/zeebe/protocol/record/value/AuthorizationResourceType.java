/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum AuthorizationResourceType {
  AUDIT_LOG(PermissionType.READ),
  AUTHORIZATION(
      PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  BATCH(
      PermissionType.CREATE,
      PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
      PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
      PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
      PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
      PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
      PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
      PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
      PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
      PermissionType.READ,
      PermissionType.UPDATE),
  COMPONENT(PermissionType.ACCESS),
  CLUSTER_VARIABLE(
      PermissionType.CREATE, PermissionType.DELETE, PermissionType.UPDATE, PermissionType.READ),
  DECISION_DEFINITION(
      PermissionType.CREATE_DECISION_INSTANCE,
      PermissionType.READ_DECISION_DEFINITION,
      PermissionType.READ_DECISION_INSTANCE,
      PermissionType.DELETE_DECISION_INSTANCE),
  DECISION_REQUIREMENTS_DEFINITION(PermissionType.READ),
  DOCUMENT(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE),
  EXPRESSION(PermissionType.EVALUATE),
  GLOBAL_LISTENER(
      PermissionType.CREATE_TASK_LISTENER,
      PermissionType.READ_TASK_LISTENER,
      PermissionType.UPDATE_TASK_LISTENER,
      PermissionType.DELETE_TASK_LISTENER),
  GROUP(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  MAPPING_RULE(
      PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  MESSAGE(PermissionType.CREATE, PermissionType.READ),
  PROCESS_DEFINITION(
      PermissionType.CREATE_PROCESS_INSTANCE,
      PermissionType.CLAIM_USER_TASK,
      PermissionType.READ_PROCESS_DEFINITION,
      PermissionType.READ_PROCESS_INSTANCE,
      PermissionType.READ_USER_TASK,
      PermissionType.UPDATE_PROCESS_INSTANCE,
      PermissionType.UPDATE_USER_TASK,
      PermissionType.MODIFY_PROCESS_INSTANCE,
      PermissionType.COMPLETE_USER_TASK,
      PermissionType.CANCEL_PROCESS_INSTANCE,
      PermissionType.DELETE_PROCESS_INSTANCE),
  RESOURCE(
      PermissionType.CREATE,
      PermissionType.READ,
      PermissionType.DELETE_DRD,
      PermissionType.DELETE_FORM,
      PermissionType.DELETE_PROCESS,
      PermissionType.DELETE_RESOURCE),
  ROLE(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  SYSTEM(
      PermissionType.READ,
      PermissionType.READ_USAGE_METRIC,
      PermissionType.READ_JOB_METRIC,
      PermissionType.UPDATE),
  TENANT(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  UNSPECIFIED(),
  USER(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  USER_TASK(
      PermissionType.READ, PermissionType.UPDATE, PermissionType.CLAIM, PermissionType.COMPLETE);

  private final Set<PermissionType> supportedPermissionTypes;

  AuthorizationResourceType(final PermissionType... supportedPermissionTypes) {
    this.supportedPermissionTypes = new HashSet<>(Arrays.asList(supportedPermissionTypes));
  }

  public Set<PermissionType> getSupportedPermissionTypes() {
    return supportedPermissionTypes;
  }

  /**
   * Returns all the resource types that are user provided. This is everything in this enum, except
   * for UNSPECIFIED. UNSPECIFIED is only used as a default internally. By having this we prevent
   * accidentally creating a wrong permission because the resource type wasn't set properly.
   *
   * @return a set of all user provided resource types
   */
  public static Set<AuthorizationResourceType> getUserProvidedResourceTypes() {
    return Arrays.stream(values()).filter(type -> type != UNSPECIFIED).collect(Collectors.toSet());
  }
}
