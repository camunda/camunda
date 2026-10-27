/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PermissionTypeTest {

  @Test
  void shouldNotClassifySuspendProcessInstancePermissionsAsReadPermissions() {
    // given / when / then
    assertThat(PermissionType.SUSPEND_PROCESS_INSTANCE.isReadPermission()).isFalse();
    assertThat(PermissionType.CREATE_BATCH_OPERATION_SUSPEND_PROCESS_INSTANCE.isReadPermission())
        .isFalse();
  }

  @Test
  void shouldSupportSuspendProcessInstanceOnProcessDefinitionResourceType() {
    // given / when / then
    assertThat(AuthorizationResourceType.PROCESS_DEFINITION.getSupportedPermissionTypes())
        .contains(PermissionType.SUSPEND_PROCESS_INSTANCE);
  }

  @Test
  void shouldSupportCreateBatchOperationSuspendProcessInstanceOnBatchResourceType() {
    // given / when / then
    assertThat(AuthorizationResourceType.BATCH.getSupportedPermissionTypes())
        .contains(PermissionType.CREATE_BATCH_OPERATION_SUSPEND_PROCESS_INSTANCE);
  }
}
