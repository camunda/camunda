/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.EndpointRegistry;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.impl.sender.NoRemoteAddressFoundException;
import io.zeebe.transport.impl.sender.OutgoingMessage;
import io.zeebe.transport.impl.sender.OutgoingRequest;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.transport.impl.sender.TransportHeaderWriter;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;

public class ClientOutputImpl implements ClientOutput {
  protected final EndpointRegistry endpointRegistry;
  protected final Sender requestManager;
  protected final Duration defaultRequestRetryTimeout;
  protected final long defaultMessageRetryTimeoutInMillis;

  public ClientOutputImpl(
      final EndpointRegistry endpointRegistry,
      final Sender requestManager,
      final Duration defaultRequestRetryTimeout,
      final Duration defaultMessageRetryTimeout) {
    this.endpointRegistry = endpointRegistry;
    this.requestManager = requestManager;
    this.defaultRequestRetryTimeout = defaultRequestRetryTimeout;
    this.defaultMessageRetryTimeoutInMillis = defaultMessageRetryTimeout.toMillis();
  }

  @Override
  public boolean sendMessage(final Integer nodeId, final BufferWriter writer) {
    final RemoteAddress remoteAddress = endpointRegistry.getEndpoint(nodeId);
    if (remoteAddress != null) {
      return sendTransportMessage(remoteAddress.getStreamId(), writer);
    } else {
      return false;
    }
  }

  private boolean sendTransportMessage(final int remoteStreamId, final BufferWriter writer) {
    final int framedMessageLength =
        TransportHeaderWriter.getFramedMessageLength(writer.getLength());
    final ByteBuffer allocatedBuffer = requestManager.allocateMessageBuffer(framedMessageLength);

    if (allocatedBuffer != null) {
      try {
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final TransportHeaderWriter headerWriter = new TransportHeaderWriter();
        headerWriter.wrapMessage(bufferView, writer, remoteStreamId);
        final long deadline = ActorClock.currentTimeMillis() + defaultMessageRetryTimeoutInMillis;

        final OutgoingMessage outgoingMessage =
            new OutgoingMessage(remoteStreamId, bufferView, deadline);

        requestManager.submitMessage(outgoingMessage);

        return true;
      } catch (final RuntimeException e) {
        requestManager.reclaimMessageBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return false;
    }
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(final Integer nodeId, final BufferWriter writer) {
    return sendRequest(nodeId, writer, defaultRequestRetryTimeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequest(
      final Integer nodeId, final BufferWriter writer, final Duration timeout) {
    return sendRequestWithRetry(() -> nodeId, this::shouldRetry, writer, timeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequestWithRetry(
      final Supplier<Integer> nodeIdSupplier, final BufferWriter writer, final Duration timeout) {
    return sendRequestWithRetry(nodeIdSupplier, this::shouldRetry, writer, timeout);
  }

  @Override
  public ActorFuture<ClientResponse> sendRequestWithRetry(
      final Supplier<Integer> nodeIdSupplier,
      final Predicate<IncomingResponse> responseInspector,
      final BufferWriter writer,
      final Duration timeout) {
    final int messageLength = writer.getLength();
    final int framedLength = TransportHeaderWriter.getFramedRequestLength(messageLength);

    final ByteBuffer allocatedBuffer = requestManager.allocateRequestBuffer(framedLength);

    if (allocatedBuffer != null) {
      try {
        final UnsafeBuffer bufferView = new UnsafeBuffer(allocatedBuffer);
        final OutgoingRequest request =
            new OutgoingRequest(
                () -> endpointRegistry.getEndpoint(nodeIdSupplier.get()),
                responseInspector,
                bufferView,
                timeout);

        request.getHeaderWriter().wrapRequest(bufferView, writer);

        return requestManager.submitRequest(request);
      } catch (final RuntimeException e) {
        requestManager.reclaimRequestBuffer(allocatedBuffer);
        throw e;
      }
    } else {
      return null;
    }
  }

  private boolean shouldRetry(final IncomingResponse response) {
    return response.getException() instanceof NoRemoteAddressFoundException;
  }
}
