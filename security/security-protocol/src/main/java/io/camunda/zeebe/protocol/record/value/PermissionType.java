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

  CREATE,
  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
  CREATE_PROCESS_INSTANCE,
  CREATE_DECISION_INSTANCE,

  READ(true),
  READ_PROCESS_INSTANCE(true),
  READ_USER_TASK(true),
  READ_DECISION_INSTANCE(true),
  READ_PROCESS_DEFINITION(true),
  READ_DECISION_DEFINITION(true),
  READ_USAGE_METRIC(true),

  UPDATE,
  UPDATE_PROCESS_INSTANCE,
  UPDATE_USER_TASK,
  CANCEL_PROCESS_INSTANCE,
  MODIFY_PROCESS_INSTANCE,

  DELETE,
  DELETE_PROCESS,
  DELETE_DRD,
  DELETE_FORM,
  DELETE_RESOURCE,
  DELETE_PROCESS_INSTANCE,
  DELETE_DECISION_INSTANCE;

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
