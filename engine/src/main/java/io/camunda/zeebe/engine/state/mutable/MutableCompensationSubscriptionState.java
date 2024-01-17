/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.CompensationSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;

public interface MutableCompensationSubscriptionState extends CompensationSubscriptionState {

  void put(final long key, CompensationSubscriptionRecord compensation);

  void update(final long key, CompensationSubscriptionRecord compensation);

  void delete(String tenantId, long processInstanceKey, long recordKey);
}
