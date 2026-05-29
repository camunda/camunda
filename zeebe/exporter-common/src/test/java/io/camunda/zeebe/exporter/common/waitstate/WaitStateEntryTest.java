/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitStateEntryTest {

  @Test
  void shouldExposeAllFieldsViaAccessors() {
    // given
    final Map<String, Object> details = Map.of("jobType", "payment", "retries", 3);
    final var entry =
        new WaitStateEntry()
            .setRootProcessInstanceKey(100L)
            .setProcessInstanceKey(200L)
            .setElementInstanceKey(300L)
            .setElementId("task-1")
            .setElementType(BpmnElementType.SERVICE_TASK)
            .setWaitStateType(WaitStateType.JOB)
            .setDetails(details)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setPartitionId(1L);

    // when / then
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("task-1");
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(entry.getDetails()).isEqualTo(details);
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(1L);
  }

  @Test
  void shouldAcceptDefaultTenantAndEmptyDetails() {
    // given / when
    final var entry =
        new WaitStateEntry()
            .setElementType(BpmnElementType.USER_TASK)
            .setWaitStateType(WaitStateType.USER_TASK)
            .setDetails(Map.of())
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getDetails()).isEmpty();
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
