/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles sending add/remove stream request to the servers. */
final class ClientStreamRequestManager<M extends BufferWriter, P extends BufferReader> {

  private static final Logger LOG = LoggerFactory.getLogger(ClientStreamRequestManager.class);
  private static final byte[] REMOVE_ALL_REQUEST = new byte[0];
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private final ClusterCommunicationService communicationService;
  private final ConcurrencyControl executor;

  ClientStreamRequestManager(
      final ClusterCommunicationService communicationService, final ConcurrencyControl executor) {
    this.communicationService = communicationService;
    this.executor = executor;
  }

  void openStream(final ClientStream<M, P> clientStream, final Collection<MemberId> servers) {
    final var request =
        new AddStreamRequest()
            .streamType(clientStream.getStreamType())
            .streamId(clientStream.getStreamId())
            .metadata(clientStream.getMetadata());

    servers.forEach(brokerId -> executor.run(() -> doAdd(request, brokerId, clientStream)));
  }

  private void doAdd(
      final AddStreamRequest request,
      final MemberId brokerId,
      final ClientStream<M, P> clientStream) {
    if (clientStream.isConnected(brokerId)) {
      return;
    }

    final CompletableFuture<byte[]> result =
        communicationService.send(
            StreamTopics.ADD.topic(),
            request,
            BufferUtil::bufferAsArray,
            Function.identity(),
            brokerId,
            REQUEST_TIMEOUT);

    result.whenComplete(
        (ignored, error) -> {
          if (error != null) {
            LOG.warn(
                "Failed to open stream {} to node {}. Will retry in {}",
                clientStream,
                brokerId,
                RETRY_DELAY);
            // TODO: define some abort conditions. We may not have to retry indefinitely.
            // For now we retry always. Eventually the request will succeed. Duplicate add request
            // are fine.
            executor.schedule(RETRY_DELAY, () -> doAdd(request, brokerId, clientStream));
          } else {
            LOG.debug("Opened stream {} to node {}", clientStream, brokerId);
            clientStream.add(brokerId);
          }
        });
  }

  void removeStream(final ClientStream<M, P> clientStream, final Collection<MemberId> servers) {
    final var request = new RemoveStreamRequest().streamId(clientStream.getStreamId());
    servers.forEach(brokerId -> executor.run(() -> doRemove(request, brokerId, clientStream)));
  }

  private void doRemove(
      final RemoveStreamRequest request,
      final MemberId brokerId,
      final ClientStream<M, P> clientStream) {

    final CompletableFuture<byte[]> result =
        communicationService.send(
            StreamTopics.REMOVE.topic(),
            request,
            BufferUtil::bufferAsArray,
            Function.identity(),
            brokerId,
            REQUEST_TIMEOUT);

    result.whenCompleteAsync(
        (ignored, error) -> {
          if (error != null && clientStream.isConnected(brokerId)) {
            // TODO: use backoff delay
            executor.schedule(RETRY_DELAY, () -> doRemove(request, brokerId, clientStream));
          } else {
            clientStream.remove(brokerId);
          }
        },
        executor::run);
  }

  void removeAll(final Collection<MemberId> servers) {
    servers.forEach(this::doRemoveAll);
  }

  private void doRemoveAll(final MemberId brokerId) {
    // Do not wait for response, as this is expected to be called while shutting down.
    communicationService.send(
        StreamTopics.REMOVE_ALL.topic(),
        REMOVE_ALL_REQUEST,
        Function.identity(),
        Function.identity(),
        brokerId,
        REQUEST_TIMEOUT);
  }
}
