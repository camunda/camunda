/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import org.junit.jupiter.api.Test;

final class PersistedDecisionTest {
  @Test
  void copyIsIndependent() {
    // given
    final var original = new PersistedDecision();
    original.wrap(
        new DecisionRecord()
            .setDecisionId("decision")
            .setDecisionName("name")
            .setDecisionKey(1)
            .setVersion(1)
            .setDecisionRequirementsId("test")
            .setDecisionRequirementsKey(1));
    final var copy = original.copy();

    // when -- modify original
    original.getDecisionId().byteArray()[0] = 'x';
    original.getDecisionRequirementsId().byteArray()[0] = 'x';

    // then -- copy is not modified
    final var copiedDecisionId = new byte[copy.getDecisionId().capacity()];
    copy.getDecisionId().getBytes(0, copiedDecisionId);
    final var copiedDecisionRequirementsId = new byte[copy.getDecisionRequirementsId().capacity()];
    copy.getDecisionRequirementsId().getBytes(0, copiedDecisionRequirementsId);

    assertEquals("decision", new String(copiedDecisionId));
    assertEquals("test", new String(copiedDecisionRequirementsId));
  }
}
