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

  /**
   * Visits all (except start event) conditional subscriptions for the given scope key.
   *
   * @param scopeKey the scope key
   * @param visitor the visitor to process each subscription
   */
  void visitByScopeKey(long scopeKey, ConditionalSubscriptionVisitor visitor);

  /**
   * Visits all conditional start event subscriptions for the given process definition key.
   *
   * @param processDefinitionKey the process definition key
   * @param visitor the visitor to process each subscription
   */
  void visitStartEventSubscriptionsByProcessDefinitionKey(
      long processDefinitionKey, ConditionalSubscriptionVisitor visitor);

  @FunctionalInterface
  interface ConditionalSubscriptionVisitor {
    void visit(ConditionalSubscription subscription);
  }
}
