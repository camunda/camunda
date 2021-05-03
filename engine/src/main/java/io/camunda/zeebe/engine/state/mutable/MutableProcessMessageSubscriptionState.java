/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.zeebe.engine.state.message.ProcessMessageSubscription;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MutableProcessMessageSubscriptionState extends ProcessMessageSubscriptionState {

  void put(final long key, ProcessMessageSubscriptionRecord record, final long commandSentTime);

  void updateToOpenedState(ProcessMessageSubscriptionRecord record);

  void updateToClosingState(ProcessMessageSubscriptionRecord record, long commandSentTime);

  void updateSentTimeInTransaction(ProcessMessageSubscription subscription, long commandSentTime);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);
}
