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
import java.util.Set;
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
  private static final String COMPENSATION_ACTIVITY_ELEMENT_ID = "compensationActivityElementId";

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
        state.get(compensation.getTenantId(), compensation.getProcessInstanceKey(), 1L).getRecord();

    assertThat(storedCompensation.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(storedCompensation.getProcessInstanceKey()).isEqualTo(PROCESS_INSTANCE_KEY);
    assertThat(storedCompensation.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(storedCompensation.getCompensableActivityId()).isEqualTo(COMPENSABLE_ACTIVITY_ID);
    assertThat(storedCompensation.getCompensableActivityScopeId())
        .isEqualTo(COMPENSABLE_ACTIVITY_SCOPE_ID);
    assertThat(storedCompensation.getThrowEventId()).isEqualTo(THROW_EVENT_ID);
    assertThat(storedCompensation.getThrowEventInstanceKey()).isEqualTo(THROW_EVENT_INSTANCE_KEY);
    assertThat(storedCompensation.getCompensationHandlerId())
        .isEqualTo(COMPENSATION_ACTIVITY_ELEMENT_ID);
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
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setCompensableActivityId(COMPENSABLE_ACTIVITY_ID)
            .setCompensableActivityScopeId(COMPENSABLE_ACTIVITY_SCOPE_ID)
            .setThrowEventId("updateThrowEventId")
            .setThrowEventInstanceKey(2L);

    state.update(1L, updatedCompensationInfo);

    final var updatedCompensation =
        state
            .get(
                compensationToUpdate.getTenantId(),
                compensationToUpdate.getProcessInstanceKey(),
                1L)
            .getRecord();

    final var notUpdatedCompensation =
        state
            .get(
                compensationToNOTUpdate.getTenantId(),
                compensationToNOTUpdate.getProcessInstanceKey(),
                2L)
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
    assertThat(subscriptions)
        .extracting(c -> c.getRecord().getCompensableActivityId())
        .contains(COMPENSABLE_ACTIVITY_ID, "anotherCompensableActivityId");
  }

  @Test
  public void shouldRemoveCompensationSubscription() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    state.put(1L, compensation);

    state.delete(TENANT_ID, PROCESS_INSTANCE_KEY, 1L);

    final var compensations =
        state.findSubscriptionsByProcessInstanceKey(TENANT_ID, PROCESS_INSTANCE_KEY);
    assertThat(compensations).isEmpty();
  }

  @Test
  public void shouldRemoveOneCompensationSubscription() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    final var compensationToRemove = createCompensation(PROCESS_INSTANCE_KEY);
    compensationToRemove.setCompensableActivityId("anotherCompensableActivityId");
    state.put(1L, compensation);
    state.put(2L, compensationToRemove);

    state.delete(TENANT_ID, PROCESS_INSTANCE_KEY, 2L);

    final Set<CompensationSubscription> compensations =
        state.findSubscriptionsByProcessInstanceKey(TENANT_ID, PROCESS_INSTANCE_KEY);
    assertThat(compensations.size()).isEqualTo(1);
    assertThat(compensations.stream().findFirst().get().getRecord().getCompensableActivityId())
        .isEqualTo(COMPENSABLE_ACTIVITY_ID);
  }

  @Test
  public void shouldFindCompensationByHandlerId() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    final var notValidCompensation = createCompensation(PROCESS_INSTANCE_KEY);
    notValidCompensation.setCompensableActivityId("anotherCompensableActivityId");
    notValidCompensation.setCompensationHandlerId("anotherCompensationActivityElementId");
    state.put(1L, compensation);
    state.put(2L, notValidCompensation);

    final var retrievedCompensation =
        state.findSubscriptionByCompensationHandlerId(
            TENANT_ID, PROCESS_INSTANCE_KEY, COMPENSATION_ACTIVITY_ELEMENT_ID);
    assertThat(retrievedCompensation.isPresent()).isTrue();
    assertThat(retrievedCompensation.get().getRecord().getCompensationHandlerId())
        .isEqualTo(COMPENSATION_ACTIVITY_ELEMENT_ID);
  }

  @Test
  public void shouldFindCompensationByThrowEventInstanceKey() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    final var notValidCompensation = createCompensation(PROCESS_INSTANCE_KEY);
    notValidCompensation.setCompensableActivityId("anotherCompensableActivityId");
    notValidCompensation.setThrowEventInstanceKey(123456789L);
    state.put(1L, compensation);
    state.put(2L, notValidCompensation);

    final Set<CompensationSubscription> compensations =
        state.findSubscriptionsByThrowEventInstanceKey(
            TENANT_ID, PROCESS_INSTANCE_KEY, THROW_EVENT_INSTANCE_KEY);
    assertThat(compensations.size()).isEqualTo(1);
    assertThat(compensations.stream().findFirst().get().getRecord().getThrowEventInstanceKey())
        .isEqualTo(THROW_EVENT_INSTANCE_KEY);
  }

  @Test
  public void shouldFindCompensationByCompensableActivityScopeId() {
    final var compensation = createCompensation(PROCESS_INSTANCE_KEY);
    final var notValidCompensation = createCompensation(PROCESS_INSTANCE_KEY);
    notValidCompensation.setCompensableActivityId("anotherCompensableActivityId");
    notValidCompensation.setCompensableActivityScopeId("anotherCompensableActivityScopeId");
    state.put(1L, compensation);
    state.put(2L, notValidCompensation);

    final var compensations =
        state.findSubscriptionsByCompensableActivityScopeId(
            TENANT_ID, PROCESS_INSTANCE_KEY, COMPENSABLE_ACTIVITY_SCOPE_ID);
    assertThat(compensations.size()).isEqualTo(1);
    assertThat(compensations.stream().findFirst().get().getRecord().getCompensableActivityScopeId())
        .isEqualTo(COMPENSABLE_ACTIVITY_SCOPE_ID);
  }

  private CompensationSubscriptionRecord createCompensation(final long key) {
    return new CompensationSubscriptionRecord()
        .setTenantId(TENANT_ID)
        .setProcessInstanceKey(key)
        .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
        .setCompensableActivityId(COMPENSABLE_ACTIVITY_ID)
        .setCompensableActivityScopeId(COMPENSABLE_ACTIVITY_SCOPE_ID)
        .setThrowEventId(THROW_EVENT_ID)
        .setThrowEventInstanceKey(THROW_EVENT_INSTANCE_KEY)
        .setCompensationHandlerId(COMPENSATION_ACTIVITY_ELEMENT_ID);
  }
}
