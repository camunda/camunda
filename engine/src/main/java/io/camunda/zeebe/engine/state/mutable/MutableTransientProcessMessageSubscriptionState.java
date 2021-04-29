/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState.ProcessMessageSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;

/**
 * Captures the transient (in-memory) state for a ProcessMessageSubscription. This is to track the
 * state of the communication between the process instance partition and the message partition
 * during opening or closing of a process message subscription. This state is not persisted to disk
 * and needs to be recovered after restart
 */
public interface MutableTransientProcessMessageSubscriptionState {

  void visitSubscriptionBefore(long deadline, ProcessMessageSubscriptionVisitor visitor);

  void updateSentTime(ProcessMessageSubscription subscription, long commandSentTime);
}
