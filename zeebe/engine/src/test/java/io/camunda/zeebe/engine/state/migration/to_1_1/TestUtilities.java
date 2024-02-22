/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

class TestUtilities {

  static LegacyMessageSubscription createLegacyMessageSubscription(
      final long key, final long elementInstanceKey) {
    final var subscription = new LegacyMessageSubscription();

    final MessageSubscriptionRecord record = createMessageSubscriptionRecord(elementInstanceKey);

    subscription.setRecord(record);
    subscription.setKey(key);

    return subscription;
  }

  static MessageSubscriptionRecord createMessageSubscriptionRecord(final long elementInstanceKey) {
    final var record = new MessageSubscriptionRecord();
    record.setProcessInstanceKey(0);
    record.setElementInstanceKey(elementInstanceKey);
    record.setMessageName(BufferUtil.wrapString("messageName"));
    return record;
  }

  static LegacyProcessMessageSubscription createLegacyProcessMessageSubscription(
      final long key, final long elementInstanceKey) {
    final var subscription = new LegacyProcessMessageSubscription();

    final ProcessMessageSubscriptionRecord record =
        createProcessMessageSubscriptionRecord(elementInstanceKey);

    subscription.setRecord(record);
    subscription.setKey(key);

    return subscription;
  }

  static ProcessMessageSubscriptionRecord createProcessMessageSubscriptionRecord(
      final long elementInstanceKey) {

    final var record = new ProcessMessageSubscriptionRecord();
    record.setProcessInstanceKey(0);
    record.setSubscriptionPartitionId(0);
    record.setElementInstanceKey(elementInstanceKey);
    record.setMessageName(BufferUtil.wrapString("messageName"));
    return record;
  }
}
