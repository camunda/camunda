/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.UUID;
import org.agrona.DirectBuffer;

/**
 * Allows to add and remove client streams.
 *
 * <p>When a client stream is added, it opens a stream to all servers. When a server pushes data to
 * this stream, the client receives it via {@link ClientStreamConsumer#push(DirectBuffer)}
 */
public interface ClientStreamer<M extends BufferWriter> {

  /**
   * Registers a client and opens a stream for the given streamType and associated Metadata with all
   * available servers. The stream is also opened for servers that are not currently reachable, but
   * available later.
   *
   * <p>NOTE: The stream to a server can be added asynchronously. So there might be a delay until
   * the consumer receives the first data.
   *
   * @param streamType type of the stream
   * @param metadata metadata associated with the stream
   * @param clientStreamConsumer consumer which process data received from the server
   * @return a unique id of the stream
   */
  ActorFuture<UUID> add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer);

  /**
   * Removes a stream that is added via {@link ClientStreamer#add(DirectBuffer, BufferWriter,
   * ClientStreamConsumer)}. After the returned future is completed, the {@link
   * ClientStreamConsumer} will not receive any more data.
   *
   * @param streamId unique id of the stream
   * @return a future which will be completed after the stream is removed
   */
  ActorFuture<Void> remove(final UUID streamId);
}
