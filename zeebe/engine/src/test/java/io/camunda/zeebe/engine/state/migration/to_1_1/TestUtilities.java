/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecordV1;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecordV1;
import io.camunda.zeebe.util.buffer.BufferUtil;

class TestUtilities {

  static LegacyMessageSubscription createLegacyMessageSubscription(
      final long key, final long elementInstanceKey) {
    final var subscription = new LegacyMessageSubscription();

    final MessageSubscriptionRecordV1 record = createMessageSubscriptionRecord(elementInstanceKey);

    subscription.setRecord(record);
    subscription.setKey(key);

    return subscription;
  }

  static MessageSubscriptionRecordV1 createMessageSubscriptionRecord(
      final long elementInstanceKey) {
    final var record = new MessageSubscriptionRecordV1();
    record.setProcessInstanceKey(0);
    record.setElementInstanceKey(elementInstanceKey);
    record.setMessageName(BufferUtil.wrapString("messageName"));
    return record;
  }

  static LegacyProcessMessageSubscription createLegacyProcessMessageSubscription(
      final long key, final long elementInstanceKey) {
    final var subscription = new LegacyProcessMessageSubscription();

    final ProcessMessageSubscriptionRecordV1 record =
        createProcessMessageSubscriptionRecord(elementInstanceKey);

    subscription.setRecord(record);
    subscription.setKey(key);

    return subscription;
  }

  static ProcessMessageSubscriptionRecordV1 createProcessMessageSubscriptionRecord(
      final long elementInstanceKey) {

    final var record = new ProcessMessageSubscriptionRecordV1();
    record.setProcessInstanceKey(0);
    record.setSubscriptionPartitionId(0);
    record.setElementInstanceKey(elementInstanceKey);
    record.setMessageName(BufferUtil.wrapString("messageName"));
    return record;
  }
}
