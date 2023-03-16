/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * A remote stream service that manages streams from the Gateways.
 *
 * @param <M> associated metadata with a stream
 * @param <P> the payload type that can be pushed to the streams
 */
public interface RemoteStreamService<M extends BufferReader, P extends BufferWriter> {
  ActorFuture<RemoteStreamer<M, P>> start(
      ActorSchedulingService actorSchedulingService, ConcurrencyControl concurrencyControl);

  ActorFuture<Void> closeAsync(ConcurrencyControl concurrencyControl);
}
