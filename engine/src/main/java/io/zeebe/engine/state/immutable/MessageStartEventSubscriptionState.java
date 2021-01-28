/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface MessageStartEventSubscriptionState {

  boolean exists(MessageStartEventSubscriptionRecord subscription);

  void visitSubscriptionsByMessageName(
      DirectBuffer messageName, Consumer<MessageStartEventSubscriptionRecord> visitor);
}
