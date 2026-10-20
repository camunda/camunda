/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.system.partitions.PartitionMessagingService;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the upload floor from a partition's leader to the partition's other members, on a subject
 * of its own: {@code event-plane-floor-{tenant}-{partition}}. A message holds the history's UUID,
 * the index and the position, as four longs.
 */
final class FloorDistribution {

  static final int MESSAGE_LENGTH = 4 * Long.BYTES;

  private static final Logger LOG = LoggerFactory.getLogger(FloorDistribution.class);

  private final PartitionMessagingService messaging;
  private final String subject;

  FloorDistribution(final PartitionMessagingService messaging, final PartitionId partitionId) {
    this.messaging = messaging;
    subject = subject(partitionId);
  }

  static String subject(final PartitionId partitionId) {
    return "event-plane-floor-%s-%d".formatted(partitionId.group(), partitionId.number());
  }

  void send(final UploadFloor floor) {
    messaging.broadcast(subject, encode(floor));
  }

  /**
   * Hands each floor received to the consumer, on the executor. A message that isn't a floor, and a
   * consumer's failure, are logged: they must not fail the executor's actor.
   */
  void subscribe(final Consumer<UploadFloor> consumer, final Executor executor) {
    messaging.subscribe(
        subject,
        message -> {
          try {
            consumer.accept(decode(message));
          } catch (final Exception e) {
            LOG.warn("Failed to take the upload floor received on {}, ignoring it", subject, e);
          }
        },
        executor);
  }

  void unsubscribe() {
    messaging.unsubscribe(subject);
  }

  static ByteBuffer encode(final UploadFloor floor) {
    final var message = ByteBuffer.allocate(MESSAGE_LENGTH);
    message
        .putLong(0, floor.history().getMostSignificantBits())
        .putLong(Long.BYTES, floor.history().getLeastSignificantBits())
        .putLong(2 * Long.BYTES, floor.index())
        .putLong(3 * Long.BYTES, floor.position());
    return message;
  }

  /**
   * @throws MalformedFloorMessageException if the message isn't as long as a floor's
   */
  static UploadFloor decode(final ByteBuffer message) {
    final var read = message.duplicate();
    if (read.remaining() != MESSAGE_LENGTH) {
      throw new MalformedFloorMessageException(MESSAGE_LENGTH, read.remaining());
    }
    return new UploadFloor(
        new UUID(read.getLong(), read.getLong()), read.getLong(), read.getLong());
  }
}
