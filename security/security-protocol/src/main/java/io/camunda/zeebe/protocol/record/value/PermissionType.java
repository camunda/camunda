/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

public enum PermissionType {
  ACCESS(true),

  CANCEL_PROCESS_INSTANCE,
  CLAIM,
  CLAIM_USER_TASK,
  COMPLETE,
  COMPLETE_USER_TASK,
  CREATE,
  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
  CREATE_DECISION_INSTANCE,
  CREATE_PROCESS_INSTANCE,
  CREATE_TASK_LISTENER,

  DELETE,
  DELETE_DECISION_INSTANCE,
  DELETE_DRD,
  DELETE_FORM,
  DELETE_PROCESS,
  DELETE_PROCESS_INSTANCE,
  DELETE_RESOURCE,
  DELETE_TASK_LISTENER,

  EVALUATE,

  MODIFY_PROCESS_INSTANCE,

  READ(true),
  READ_DECISION_DEFINITION(true),
  READ_DECISION_INSTANCE(true),
  READ_JOB_METRIC(true),
  READ_PROCESS_DEFINITION(true),
  READ_PROCESS_INSTANCE(true),
  READ_USAGE_METRIC(true),
  READ_USER_TASK(true),
  READ_TASK_LISTENER(true),

  UPDATE,
  UPDATE_PROCESS_INSTANCE,
  UPDATE_USER_TASK,
  UPDATE_TASK_LISTENER;

  private final boolean isReadPermission;

  PermissionType(final boolean isReadPermission) {
    this.isReadPermission = isReadPermission;
  }

  PermissionType() {
    isReadPermission = false;
  }

  public boolean isReadPermission() {
    return isReadPermission;
  }
}
