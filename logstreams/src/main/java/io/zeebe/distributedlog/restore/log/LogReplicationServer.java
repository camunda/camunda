/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

import java.util.concurrent.CompletableFuture;

public interface LogReplicationServer extends AutoCloseable {

  /**
   * Start serving {@link LogReplicationRequest} requests to the given handler.
   *
   * @param server handler to handle incoming requests
   * @return a future which will complete once the server is ready to accept requests
   */
  CompletableFuture<Void> serve(Handler server);

  @Override
  void close();

  @FunctionalInterface
  interface Handler {

    /**
     * Each request will contain a range of event positions, and expect the returned events to have
     * positions strictly contained within that range. Furthermore, if {@link
     * LogReplicationRequest#includeFromPosition()} is true the first event serialized in the
     * response must be the event at position equal to request {@link
     * LogReplicationRequest#getFromPosition()}, other the first event must be the the next event
     * right after the one at position equal to the request {@link
     * LogReplicationRequest#getFromPosition()}. If no event is found locally at that position, the
     * returned response must be invalid.
     *
     * <p>Additionally, events must be serialized in the order they were read on the local log, and
     * the last event position must be less than or equal to the {@link
     * LogReplicationRequest#getToPosition()}. If that position is less than the requested {@code
     * toPosition}, but there are more events locally with positions less than the {@code
     * toPosition}, then the response should indicate that and {@link
     * LogReplicationResponse#hasMoreAvailable()} should be true.
     *
     * @param request the request to server
     * @return response to return to the sender
     */
    LogReplicationResponse onReplicationRequest(LogReplicationRequest request);
  }
}
