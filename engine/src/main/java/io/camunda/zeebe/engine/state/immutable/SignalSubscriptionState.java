/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface SignalSubscriptionState {

  boolean exists(SignalSubscriptionRecord subscription);

  void visitBySignalName(DirectBuffer signalName, SignalSubscriptionVisitor visitor);

  /**
   * Visit all subscriptions with the given process definition key.
   *
   * @param processDefinitionKey the key of the process definition the subscription belongs to
   * @param visitor the function that is called for each subscription
   */
  void visitStartEventSubscriptionsByProcessDefinitionKey(
      long processDefinitionKey, SignalSubscriptionVisitor visitor);

  @FunctionalInterface
  interface SignalSubscriptionVisitor {
    void visit(SignalSubscription subscription);
  }
}
