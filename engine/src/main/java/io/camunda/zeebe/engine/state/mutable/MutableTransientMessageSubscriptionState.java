/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState.MessageSubscriptionVisitor;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;

/**
 * Captures the transient (in-memory) state for a MessageSubscription. This is to track the state of
 * the communication between the message partition and the during correlation of message
 * subscription. This state is not persisted to disk and needs to be recovered after restart
 */
public interface MutableTransientMessageSubscriptionState {

  void visitSubscriptionBefore(long deadline, MessageSubscriptionVisitor visitor);

  void updateCommandSentTime(MessageSubscriptionRecord record, long commandSentTime);
}
