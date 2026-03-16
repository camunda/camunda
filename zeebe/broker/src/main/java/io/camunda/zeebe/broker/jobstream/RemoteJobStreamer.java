/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.transport.stream.impl.messages.JobAvailableNotificationEncoder;
import io.camunda.zeebe.transport.stream.impl.messages.MessageHeaderEncoder;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

public final class RemoteJobStreamer implements JobStreamer {
  static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";
  static final String JOBS_AVAILABLE_BY_PARTITION_TOPIC = "jobsAvailableByPartition";

  private static final ThreadLocal<MessageHeaderEncoder> HEADER_ENCODER =
      ThreadLocal.withInitial(MessageHeaderEncoder::new);
  private static final ThreadLocal<JobAvailableNotificationEncoder> NOTIFICATION_ENCODER =
      ThreadLocal.withInitial(JobAvailableNotificationEncoder::new);

  private final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate;
  private final ClusterEventService eventService;

  public RemoteJobStreamer(
      final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate,
      final ClusterEventService eventService) {
    this.delegate = delegate;
    this.eventService = eventService;
  }

  @Override
  public void notifyWorkAvailable(final String jobType, final int partitionId) {
    // Broadcast on the legacy topic for backward compatibility with old gateways.
    eventService.broadcast(JOBS_AVAILABLE_TOPIC, jobType);

    // Broadcast on the SBE-encoded topic for new gateways that support partition-targeted polling.
    if (partitionId > 0) {
      eventService.broadcast(
          JOBS_AVAILABLE_BY_PARTITION_TOPIC,
          serializeNotification(jobType, partitionId),
          Function.identity());
    }
  }

  private static byte[] serializeNotification(final String jobType, final int partitionId) {
    final var headerEncoder = HEADER_ENCODER.get();
    final var notificationEncoder = NOTIFICATION_ENCODER.get();
    final var buffer =
        new ExpandableArrayBuffer(
            MessageHeaderEncoder.ENCODED_LENGTH
                + JobAvailableNotificationEncoder.BLOCK_LENGTH
                + JobAvailableNotificationEncoder.jobTypeHeaderLength()
                + jobType.length());
    notificationEncoder
        .wrapAndApplyHeader(buffer, 0, headerEncoder)
        .partitionId(partitionId)
        .jobType(jobType);
    final var bytes = new byte[headerEncoder.encodedLength() + notificationEncoder.encodedLength()];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  @Override
  public Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> filter) {
    return delegate.streamFor(jobType, filter).map(RemoteJobStream::new);
  }
}
