/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.compensation.CompensationSubscription;
import java.util.Optional;
import java.util.Set;

public interface CompensationSubscriptionState {

  CompensationSubscription get(String tenantId, long processInstanceKey, long key);

  Set<CompensationSubscription> findSubscriptionsByProcessInstanceKey(
      String tenantId, long processInstanceKey);

  Optional<CompensationSubscription> findSubscriptionByCompensationHandlerId(
      String tenantId, long processInstanceKey, String compensationHandlerId);

  Set<CompensationSubscription> findSubscriptionsByThrowEventInstanceKey(
      String tenantId, long processInstanceKey, long throwEventInstanceKey);

  Set<CompensationSubscription> findSubscriptionsByCompensableActivityScopeId(
      String tenantId, long processInstanceKey, String compensableActivityScopeId);

  Set<CompensationSubscription> findSubprocessSubscriptions(
      String tenantId, long processInstanceKey);
}
