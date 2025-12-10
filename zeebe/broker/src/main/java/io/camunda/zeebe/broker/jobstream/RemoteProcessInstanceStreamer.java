/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.engine.processing.streamprocessor.ProcessInstanceStreamer;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;

public class RemoteProcessInstanceStreamer implements ProcessInstanceStreamer {

  private final RemoteStreamer<Long, ProcessInstanceRecord> delegate;
  private final ClusterEventService eventService;

  public RemoteProcessInstanceStreamer(
      final RemoteStreamer<Long, ProcessInstanceRecord> delegate,
      final ClusterEventService eventService) {
    this.delegate = delegate;
    this.eventService = eventService;
  }

  @Override
  public Optional<ProcessInstanceStream> streamFor(
      final long processInstanceKey, final Predicate<Long> filter) {
    final var byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
    final var streamType = new UnsafeBuffer(byteBuffer);
    streamType.putLong(0, processInstanceKey);
    return delegate.streamFor(streamType, filter).map(RemoteProcessInstanceStream::new);
  }
}
