/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ConditionalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;

public interface MutableConditionalSubscriptionState extends ConditionalSubscriptionState {

  void put(final long key, ConditionalSubscriptionRecord subscription);

  void putStart(final long key, ConditionalSubscriptionRecord subscription);

  void delete(final long key, ConditionalSubscriptionRecord subscription);

  void deleteStart(long key, ConditionalSubscriptionRecord record);
}
