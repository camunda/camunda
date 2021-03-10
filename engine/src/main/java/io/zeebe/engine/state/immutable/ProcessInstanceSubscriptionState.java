/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import org.agrona.DirectBuffer;

public interface WorkflowInstanceSubscriptionState {

  WorkflowInstanceSubscription getSubscription(long elementInstanceKey, DirectBuffer messageName);

  void visitElementSubscriptions(
      long elementInstanceKey, WorkflowInstanceSubscriptionVisitor visitor);

  void visitSubscriptionBefore(long deadline, WorkflowInstanceSubscriptionVisitor visitor);

  boolean existSubscriptionForElementInstance(long elementInstanceKey, DirectBuffer messageName);

  @FunctionalInterface
  interface WorkflowInstanceSubscriptionVisitor {
    boolean visit(WorkflowInstanceSubscription subscription);
  }
}
