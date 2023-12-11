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
    final var compensation = createCompensation();
    state.put(1L, compensation);
    assertThat(
            state.get(
                compensation.getTenantId(),
                compensation.getProcessInstanceKey(),
                compensation.getCompensableActivityId()))
        .isNotNull();
  }

  private CompensationSubscriptionRecord createCompensation() {
    return new CompensationSubscriptionRecord()
        .setTenantId("tenantId")
        .setProcessInstanceKey(1L)
        .setProcessDefinitionKey(1L)
        .setCompensableActivityId("compensableActivityId")
        .setCompensableActivityScopeId(1L)
        .setThrowEventId("throwEventId")
        .setThrowEventInstanceKey(1L);
  }
}
