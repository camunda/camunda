/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class MessageStartEventSubscriptionRecordTest {

  @Test
  void shouldCopyStorageOrdinalOnWrap() {
    // given
    final MessageStartEventSubscriptionRecord original =
        new MessageStartEventSubscriptionRecord()
            .setProcessDefinitionKey(22334L)
            .setMessageName(wrapString("name"))
            .setStartEventId(wrapString("start"))
            .setBpmnProcessId(wrapString("process"))
            .setStorageOrdinal(1234);

    // when
    final MessageStartEventSubscriptionRecord copy = new MessageStartEventSubscriptionRecord();
    copy.wrap(original);

    // then
    assertThat(copy.getStorageOrdinal()).isEqualTo(1234);
  }
}
