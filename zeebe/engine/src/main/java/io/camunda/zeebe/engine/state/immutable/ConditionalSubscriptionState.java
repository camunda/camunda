/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.conditional.ConditionalSubscription;

public interface ConditionalSubscriptionState {

  boolean exists(String tenantId, long subscriptionKey);

  void visitByScopeKey(long scopeKey, ConditionalSubscriptionVisitor visitor);

  void visitStartEventSubscriptionsByProcessDefinitionKey(
      long processDefinitionKey, ConditionalSubscriptionVisitor visitor);

  @FunctionalInterface
  interface ConditionalSubscriptionVisitor {
    void visit(ConditionalSubscription subscription);
  }
}
