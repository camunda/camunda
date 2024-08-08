/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MutableProcessMessageSubscriptionState extends ProcessMessageSubscriptionState {

  void put(final long key, ProcessMessageSubscriptionRecord record);

  void updateToOpeningState(ProcessMessageSubscriptionRecord record);

  void updateToOpenedState(ProcessMessageSubscriptionRecord record);

  void updateToClosingState(ProcessMessageSubscriptionRecord record);

  boolean remove(long elementInstanceKey, DirectBuffer messageName, final String tenantId);

  void update(final long key, ProcessMessageSubscriptionRecord record);
}
