/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.Test;

final class CompensationSubscriptionTest {
  @Test
  void copyIsIndependent() {
    // given
    final var original = new CompensationSubscription();
    final var originalVariables = new UnpackedObject(0);
    original
        .setKey(1)
        .setRecord(
            new CompensationSubscriptionRecord()
                .setProcessInstanceKey(1)
                .setProcessDefinitionKey(2)
                .setVariables(BufferUtil.createCopy(originalVariables)));
    final var copy = original.copy();

    // when -- modify original
    original.getRecord().getVariablesBuffer().byteArray()[0] = 0;

    // then -- copy is not modified
    assertThat(copy.getRecord().getVariablesBuffer())
        .isEqualTo(BufferUtil.createCopy(originalVariables));
  }
}
