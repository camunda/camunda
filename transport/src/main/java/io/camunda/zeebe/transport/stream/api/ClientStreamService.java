/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * Manages an instance of {@link ClientStreamer}. Intended to be the main entry point when setting
 * up the client side for remote streams, primarily via {@link
 * io.camunda.zeebe.transport.TransportFactory#createRemoteStreamClient(ClusterCommunicationService,
 * ClientStreamMetrics)}.
 *
 * @param <M> the type of the streaming metadata
 */
public interface ClientStreamService<M extends BufferWriter> extends AsyncClosable {

  /**
   * Starts the service, optionally with the given actor scheduling service. Assumes the scheduling
   * service is already running.
   */
  ActorFuture<Void> start(final ActorSchedulingService schedulingService);

  /**
   * A callback to be invoked when a new streaming server is added. Implementations should be
   * idempotent.
   */
  void onServerJoined(final MemberId memberId);

  /**
   * A callback to be invoked when a new streaming server is removed. Implementations should be
   * idempotent.
   */
  void onServerRemoved(final MemberId memberId);

  /** Returns the managed {@link ClientStreamer} associated with this service. */
  ClientStreamer<M> streamer();
}
