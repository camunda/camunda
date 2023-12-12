/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CompensationSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableCompensationSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getProcessingState().getCompensationSubscriptionState();
  }

  @Test
  public void shouldPutCompensationSubscriptionRecord() {
    final var compensation = createCompensation(1L);
    state.put(1L, compensation);
    assertThat(
            state.get(
                compensation.getTenantId(),
                compensation.getProcessInstanceKey(),
                compensation.getCompensableActivityId()))
        .isNotNull();
  }

  @Test
  public void shouldUpdateAllTheCompensationSubscriptionWithSameTenantIdAndProcessInstanceKey() {
    final var compensationToUpdate = createCompensation(1L);
    final var compensationToNOTUpdate = createCompensation(2L);

    state.put(1L, compensationToUpdate);
    state.put(2L, compensationToNOTUpdate);

    final var updatedCompensationInfo =
        new CompensationSubscriptionRecord()
            .setTenantId("tenantId")
            .setProcessInstanceKey(1L)
            .setThrowEventId("updateThrowEventId")
            .setThrowEventInstanceKey(2L);

    state.update(updatedCompensationInfo);

    final var updatedCompensation =
        state
            .get(
                compensationToUpdate.getTenantId(),
                compensationToUpdate.getProcessInstanceKey(),
                compensationToUpdate.getCompensableActivityId())
            .getRecord();

    final var notUpdatedCompensation =
        state
            .get(
                compensationToNOTUpdate.getTenantId(),
                compensationToNOTUpdate.getProcessInstanceKey(),
                compensationToNOTUpdate.getCompensableActivityId())
            .getRecord();

    assertThat(updatedCompensation.getCompensableActivityId()).isEqualTo("compensableActivityId");
    assertThat(updatedCompensation.getThrowEventId()).isEqualTo("updateThrowEventId");
    assertThat(updatedCompensation.getThrowEventInstanceKey()).isEqualTo(2L);

    assertThat(notUpdatedCompensation.getCompensableActivityId())
        .isEqualTo("compensableActivityId");
    assertThat(notUpdatedCompensation.getThrowEventId()).isEqualTo("throwEventId");
    assertThat(notUpdatedCompensation.getThrowEventInstanceKey()).isEqualTo(1L);
  }

  private CompensationSubscriptionRecord createCompensation(final long key) {
    return new CompensationSubscriptionRecord()
        .setTenantId("tenantId")
        .setProcessInstanceKey(key)
        .setProcessDefinitionKey(1L)
        .setCompensableActivityId("compensableActivityId")
        .setCompensableActivityScopeId(1L)
        .setThrowEventId("throwEventId")
        .setThrowEventInstanceKey(1L);
  }
}
