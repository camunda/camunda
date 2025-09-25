/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.mutable;

import io.camunda.zeebe.engine.common.state.immutable.CompensationSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;

public interface MutableCompensationSubscriptionState extends CompensationSubscriptionState {

  void put(final long key, CompensationSubscriptionRecord compensation);

  void update(final long key, CompensationSubscriptionRecord compensation);

  void delete(String tenantId, long processInstanceKey, long recordKey);
}
