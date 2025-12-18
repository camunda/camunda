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

  /**
   * Checks whether a conditional subscription exists for the given tenant ID and subscription key.
   *
   * @param tenantId the tenant ID
   * @param subscriptionKey the subscription key
   * @return true if the conditional subscription exists, false otherwise
   */
  boolean exists(String tenantId, long subscriptionKey);

  /**
   * Checks whether any conditional subscriptions exist for the given process definition key. Please
   * note that this DOES NOT include conditional start event subscriptions but only boundary events,
   * intermediate catch events, and event subprocess start events.
   *
   * <p>This is used to quickly check whether any conditional subscriptions need to be visited when
   * a variable is updated in a given scope.
   *
   * @param processDefinitionKey the process definition key
   * @return true if any conditional start event subscriptions exist, false otherwise
   */
  boolean exists(long processDefinitionKey);

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
    boolean visit(ConditionalSubscription subscription);
  }
}
