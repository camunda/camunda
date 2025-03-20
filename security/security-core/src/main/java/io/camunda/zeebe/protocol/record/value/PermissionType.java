/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

public enum PermissionType {
  ACCESS,

  CREATE,
  CREATE_PROCESS_INSTANCE,
  CREATE_DECISION_INSTANCE,

  READ,
  READ_PROCESS_INSTANCE,
  READ_USER_TASK,
  READ_DECISION_INSTANCE,
  READ_PROCESS_DEFINITION,
  READ_DECISION_DEFINITION,

  UPDATE,
  UPDATE_PROCESS_INSTANCE,
  UPDATE_USER_TASK,

  DELETE,
  DELETE_PROCESS,
  DELETE_DRD,
  DELETE_FORM,
  DELETE_RESOURCE,
  DELETE_PROCESS_INSTANCE,
  DELETE_DECISION_INSTANCE
}
