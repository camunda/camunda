/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.conditional.ConditionSubscription;
import java.util.List;

public interface ConditionSubscriptionState {

  List<ConditionSubscription> getSubscriptionsByScopeKey(String tenantId, long scopeKey);

  boolean exists(String tenantId, long subscriptionKey);

  void visitByScopeKey(long scopeKey, ConditionSubscriptionVisitor visitor);

  @FunctionalInterface
  interface ConditionSubscriptionVisitor {
    void visit(ConditionSubscription subscription);
  }
}
