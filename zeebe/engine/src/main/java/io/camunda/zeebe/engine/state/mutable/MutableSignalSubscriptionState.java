/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MutableSignalSubscriptionState extends SignalSubscriptionState {

  void put(final long key, SignalSubscriptionRecord subscription);

  void remove(long subscriptionKey, DirectBuffer signalName, String tenantId);
}
