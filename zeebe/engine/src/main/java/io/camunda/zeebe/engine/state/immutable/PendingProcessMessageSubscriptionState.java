/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState.ProcessMessageSubscriptionVisitor;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;

/**
 * Captures the transient (in-memory) state for a ProcessMessageSubscription. This is to track the
 * state of the communication between the process instance partition and the message partition
 * during opening or closing of a process message subscription. This state is not persisted to disk
 * and needs to be recovered after restart
 */
public interface PendingProcessMessageSubscriptionState {

  /**
   * Visits all pending process message subscriptions where a command hasn't been sent out since a
   * given deadline. The visitor is called for each subscription, from the oldest to the newest.
   */
  void visitPending(long deadline, ProcessMessageSubscriptionVisitor visitor);

  /**
   * Should be called when a pending subscription is sent out. This is used to keep track of the
   * last time a command was sent out for a subscription. Freshly sent-out subscriptions are not
   * visited by {@link #visitPending(long, ProcessMessageSubscriptionVisitor)}.
   */
  void onSent(ProcessMessageSubscriptionRecord record, long timestampMs);
}
