/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Abstracts away messaging to other members of a partition - add operations as needed. */
public interface PartitionMessagingService {

  /**
   * Subscribes to a given subject - if another member of the partition sends a message on this
   * topic, the consumer will be notified with the given payload. Each call is considered a new
   * subscription.
   *
   * @param subject the subject to subscribe to
   * @param consumer the consumer which handles the payload
   * @param executor the executor on which the consumer is called
   */
  void subscribe(
      final String subject, final Consumer<ByteBuffer> consumer, final Executor executor);

  /**
   * Broadcasts the given payload to all other members of the partition; should log if a member is
   * not subscribed to a given topic, but not fail.
   *
   * @param subject the subject on which to broadcast the payload
   * @param payload the payload to send
   */
  void broadcast(final String subject, final ByteBuffer payload);

  /**
   * Unsubcribes from the given subject, such that no messages after this call are handled by any
   * previously registered consumer. If none registered, does nothing.
   *
   * @param subject the subject from which to unsubscribe
   */
  void unsubscribe(final String subject);
}
