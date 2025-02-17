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

public enum AuthorizationResourceType {
  AUTHORIZATION(
      PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE, PermissionType.CREATE),
  MAPPING_RULE(
      PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  MESSAGE(PermissionType.CREATE, PermissionType.READ),
  BATCH(PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE),
  APPLICATION(PermissionType.ACCESS),
  SYSTEM(PermissionType.READ, PermissionType.UPDATE),
  TENANT(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  RESOURCE(
      PermissionType.CREATE,
      PermissionType.DELETE_FORM,
      PermissionType.DELETE_PROCESS,
      PermissionType.DELETE_DRD),
  PROCESS_DEFINITION(
      PermissionType.READ_PROCESS_DEFINITION,
      PermissionType.READ_PROCESS_INSTANCE,
      PermissionType.READ_USER_TASK,
      PermissionType.UPDATE_PROCESS_INSTANCE,
      PermissionType.UPDATE_USER_TASK,
      PermissionType.CREATE_PROCESS_INSTANCE,
      PermissionType.DELETE_PROCESS_INSTANCE),
  DECISION_REQUIREMENTS_DEFINITION(
      PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  DECISION_DEFINITION(
      PermissionType.READ_DECISION_DEFINITION,
      PermissionType.READ_DECISION_INSTANCE,
      PermissionType.CREATE_DECISION_INSTANCE,
      PermissionType.DELETE_DECISION_INSTANCE),
  GROUP(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  USER(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  ROLE(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE),
  UNSPECIFIED();

  private final Set<PermissionType> supportedPermissionTypes;

  AuthorizationResourceType(final PermissionType... supportedPermissionTypes) {
    this.supportedPermissionTypes = new HashSet<>(Arrays.asList(supportedPermissionTypes));
  }

  public Set<PermissionType> getSupportedPermissionTypes() {
    return supportedPermissionTypes;
  }
}
