/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateElementType;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitStateEntryTest {

  @Test
  void shouldExposeAllFieldsViaAccessors() {
    // given
    final Map<String, Object> details = Map.of("jobType", "payment", "retries", 3);
    final var entry =
        new WaitStateEntry(
            100L,
            200L,
            300L,
            "task-1",
            WaitStateElementType.SERVICE_TASK,
            WaitStateType.JOB,
            details,
            TenantOwned.DEFAULT_TENANT_IDENTIFIER,
            1L);

    // when / then
    assertThat(entry.rootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.processInstanceKey()).isEqualTo(200L);
    assertThat(entry.elementInstanceKey()).isEqualTo(300L);
    assertThat(entry.elementId()).isEqualTo("task-1");
    assertThat(entry.elementType()).isEqualTo(WaitStateElementType.SERVICE_TASK);
    assertThat(entry.waitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(entry.details()).isEqualTo(details);
    assertThat(entry.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.partitionId()).isEqualTo(1L);
  }

  @Test
  void shouldAcceptDefaultTenantAndEmptyDetails() {
    // given / when
    final var entry =
        new WaitStateEntry(
            0L,
            0L,
            0L,
            "task-1",
            WaitStateElementType.USER_TASK,
            WaitStateType.USER_TASK,
            Map.of(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER,
            1L);

    // then
    assertThat(entry.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.details()).isEmpty();
  }

  @Test
  void shouldExposeExpectedWaitStateElementTypes() {
    // when / then
    assertThat(WaitStateElementType.values())
        .containsExactlyInAnyOrder(
            WaitStateElementType.SERVICE_TASK,
            WaitStateElementType.SEND_TASK,
            WaitStateElementType.RECEIVE_TASK,
            WaitStateElementType.USER_TASK,
            WaitStateElementType.BUSINESS_RULE_TASK,
            WaitStateElementType.SCRIPT_TASK,
            WaitStateElementType.INTERMEDIATE_CATCH_EVENT,
            WaitStateElementType.BOUNDARY_EVENT,
            WaitStateElementType.EVENT_BASED_GATEWAY,
            WaitStateElementType.CALL_ACTIVITY);
  }

  @Test
  void shouldExposeExpectedWaitStateTypes() {
    // when / then
    assertThat(WaitStateType.values())
        .containsExactlyInAnyOrder(
            WaitStateType.JOB,
            WaitStateType.MESSAGE,
            WaitStateType.USER_TASK,
            WaitStateType.TIMER,
            WaitStateType.SIGNAL,
            WaitStateType.INCIDENT,
            WaitStateType.CALL_ACTIVITY);
  }
}
