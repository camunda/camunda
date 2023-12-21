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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class CompensationSubscriptionStateTest {

  private static final String TENANT_ID = "tenantId";
  private static final long PROCESS_INSTANCE_KEY = 1L;
  private static final long PROCESS_DEFINITION_KEY = 2L;
  private static final String COMPENSABLE_ACTIVITY_ID = "compensableActivityId";
  private static final String COMPENSABLE_ACTIVITY_SCOPE_ID = "compensableActivityScopeId";
  private static final String THROW_EVENT_ID = "throwEventId";
  private static final long THROW_EVENT_INSTANCE_KEY = 4L;

  private MutableCompensationSubscriptionState state;
  private MutableProcessingState processingState;

  @BeforeEach
  public void setUp() {
    state = processingState.getCompensationSubscriptionState();
  }

  @Test
  public void shouldPutCompensationSubscriptionRecord() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    state.put(1L, compensation);

    final var storedCompensation =
        state
            .get(
                compensation.getTenantId(),
                compensation.getProcessInstanceKey(),
                compensation.getCompensableActivityId())
            .getRecord();

    assertThat(storedCompensation.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(storedCompensation.getProcessInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
    assertThat(storedCompensation.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(storedCompensation.getCompensableActivityId()).isEqualTo(COMPENSABLE_ACTIVITY_ID);
    assertThat(storedCompensation.getCompensableActivityScopeId())
        .isEqualTo(COMPENSABLE_ACTIVITY_SCOPE_ID);
    assertThat(storedCompensation.getThrowEventId()).isEqualTo(THROW_EVENT_ID);
    assertThat(storedCompensation.getThrowEventInstanceKey()).isEqualTo(THROW_EVENT_INSTANCE_KEY);
  }

  @Test
  public void shouldUpdateAllTheCompensationSubscriptionWithSameTenantIdAndProcessInstanceKey() {
    final var compensationToUpdate = createCompensation(PROCESS_INSTANCE_KEY);
    final var compensationToNOTUpdate = createCompensation(2L);

    state.put(1L, compensationToUpdate);
    state.put(2L, compensationToNOTUpdate);

    final var updatedCompensationInfo =
        new CompensationSubscriptionRecord()
            .setTenantId(TENANT_ID)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
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

    assertThat(updatedCompensation.getCompensableActivityId()).isEqualTo(COMPENSABLE_ACTIVITY_ID);
    assertThat(updatedCompensation.getThrowEventId()).isEqualTo("updateThrowEventId");
    assertThat(updatedCompensation.getThrowEventInstanceKey()).isEqualTo(2L);

    assertThat(notUpdatedCompensation.getCompensableActivityId())
        .isEqualTo(COMPENSABLE_ACTIVITY_ID);
    assertThat(notUpdatedCompensation.getThrowEventId()).isEqualTo(THROW_EVENT_ID);
    assertThat(notUpdatedCompensation.getThrowEventInstanceKey())
        .isEqualTo(THROW_EVENT_INSTANCE_KEY);
  }

  @Test
  public void shouldFindCompensationSubscriptionByProcessInstanceKey() {
    final var compensationToRetrieve = createCompensation(PROCESS_INSTANCE_KEY);
    final var compensationToRetrieve2 = createCompensation(PROCESS_INSTANCE_KEY);
    compensationToRetrieve2.setCompensableActivityId("anotherCompensableActivityId");
    final var compensationToNOTRetrieve = createCompensation(2L);

    state.put(1L, compensationToRetrieve);
    state.put(2L, compensationToRetrieve2);
    state.put(3L, compensationToNOTRetrieve);

    final var subscriptions =
        state.findSubscriptionsByProcessInstanceKey(TENANT_ID, PROCESS_INSTANCE_KEY);
    assertThat(subscriptions.size()).isEqualTo(2);
    assertThat(subscriptions).contains(COMPENSABLE_ACTIVITY_ID, "anotherCompensableActivityId");
  }

  private CompensationSubscriptionRecord createCompensation(final long key) {
    return new CompensationSubscriptionRecord()
        .setTenantId(TENANT_ID)
        .setProcessInstanceKey(key)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
        .setCompensableActivityId(COMPENSABLE_ACTIVITY_ID)
        .setCompensableActivityScopeId(COMPENSABLE_ACTIVITY_SCOPE_ID)
        .setThrowEventId(THROW_EVENT_ID)
        .setThrowEventInstanceKey(THROW_EVENT_INSTANCE_KEY);
  }
}
