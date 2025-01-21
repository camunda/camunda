/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.BooleanType;
import io.atomix.cluster.messaging.HeartbeatSetupRequestDecoder;
import io.atomix.cluster.messaging.HeartbeatSetupRequestEncoder;
import io.atomix.cluster.messaging.HeartbeatSetupResponseDecoder;
import io.atomix.cluster.messaging.HeartbeatSetupResponseEncoder;
import io.atomix.cluster.messaging.MessageHeaderDecoder;
import io.atomix.cluster.messaging.MessageHeaderEncoder;
import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public abstract sealed class HeartbeatSetupHandler extends ChannelDuplexHandler {

  private static final String HEARTBEAT_SETUP_SUBJECT = "internal-heartbeat-setup";
  protected final String afterHandler;
  protected final Logger log;
  protected final boolean forwardHeartbeats;
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  protected HeartbeatSetupHandler(
      final String afterHandler, final Logger log, final boolean forwardHeartbeats) {
    this.afterHandler = afterHandler;
    this.log = log;
    this.forwardHeartbeats = forwardHeartbeats;
  }

  /**
   * The client sends a message to exchange the heartbeatTimeout with the server and then waits for
   * the server to confirm that heartbeats are enabled. If the server supports heartbeats, it sets
   * up {@link HeartbeatHandler.Client}
   */
  public static final class Client extends HeartbeatSetupHandler {

    final AtomicLong messageIdGenerator;
    private final Address advertisedAddress;
    private final Duration heartbeatTimeout;
    private final Duration heartbeatInterval;
    private final HeartbeatSetupRequestEncoder requestEncoder = new HeartbeatSetupRequestEncoder();
    private final HeartbeatSetupResponseDecoder responseDecoder =
        new HeartbeatSetupResponseDecoder();
    private long heartbeatRequestId;

    public Client(
        final String afterHandler,
        final Logger log,
        final AtomicLong messageIdGenerator,
        final Address advertisedAddress,
        final Duration heartbeatTimeout,
        final Duration heartbeatInterval,
        final boolean forwardHeartbeats) {
      super(afterHandler, log, forwardHeartbeats);
      this.messageIdGenerator = messageIdGenerator;
      this.advertisedAddress = advertisedAddress;
      this.heartbeatTimeout = heartbeatTimeout;
      this.heartbeatInterval = heartbeatInterval;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
      if (ctx.channel().isActive()) {
        ctx.writeAndFlush(createHeartbeatRequest());
      }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      // check if it's the response to our HeartbeatRequest
      if (msg instanceof final ProtocolReply reply && reply.id() == heartbeatRequestId) {
        final var isHeartbeatEnabled = heartbeatEnabled(reply.payload());
        log.trace(
            "Received HeartbeatResponse: id={}, heartbeatEnabled={}",
            reply.id(),
            isHeartbeatEnabled);
        if (isHeartbeatEnabled) {
          ctx.pipeline()
              .addAfter(
                  afterHandler,
                  HeartbeatHandler.HEARTBEAT_HANDLER_NAME,
                  new HeartbeatHandler.Client(
                      log,
                      messageIdGenerator,
                      advertisedAddress,
                      heartbeatTimeout,
                      heartbeatInterval,
                      forwardHeartbeats));
        }
        // the heartbeat setup is done, no need to stay in the pipeline
        ctx.pipeline().remove(this);
        ReferenceCountUtil.release(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    // The message is fixed in size, so we can allocate an exact buffer
    private byte[] allocateRequestBuffer() {
      return new byte
          [MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatSetupRequestEncoder.BLOCK_LENGTH];
    }

    private ProtocolRequest createHeartbeatRequest() {
      final var bytes = allocateRequestBuffer();
      final var buffer = new UnsafeBuffer(bytes);
      requestEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
      requestEncoder.heartbeatTimeout(heartbeatTimeout.toMillis());
      heartbeatRequestId = messageIdGenerator.incrementAndGet();
      return new ProtocolRequest(
          heartbeatRequestId, advertisedAddress, HEARTBEAT_SETUP_SUBJECT, bytes);
    }

    private boolean heartbeatEnabled(final byte[] bytes) {
      final var buffer = new UnsafeBuffer(bytes);
      try {
        responseDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return responseDecoder.heartbeatEnabled().equals(BooleanType.TRUE);
      } catch (final IllegalStateException e) {
        log.warn("Unable to decode heartbeat response, heartbeats are disabled.", e);
        return false;
      }
    }
  }

  /**
   * The server waits for a message to enable heartbeats on this connection. When such request is
   * received, he replies to the client in order to enable the heartbeats. In that case, it sets up
   * a {@link HeartbeatHandler.Server}
   */
  public static final class Server extends HeartbeatSetupHandler {
    private final HeartbeatSetupRequestDecoder requestDecoder = new HeartbeatSetupRequestDecoder();
    private final HeartbeatSetupResponseEncoder responseEncoder =
        new HeartbeatSetupResponseEncoder();

    public Server(final String afterHandler, final Logger log, final boolean forwardHeartbeats) {
      super(afterHandler, log, forwardHeartbeats);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
      // check if it's a heartbeat request
      if (msg instanceof final ProtocolRequest request
          && request.subject().equals(HEARTBEAT_SETUP_SUBJECT)) {
        final var timeout = heartbeatTimeout(request.payload());
        if (timeout > 0) {
          log.trace(
              "Received heartbeat request: id:{}, heartbeatTimeout={} millis",
              request.id(),
              timeout);
          ctx.pipeline()
              .addAfter(
                  afterHandler,
                  HeartbeatHandler.HEARTBEAT_HANDLER_NAME,
                  new HeartbeatHandler.Server(log, Duration.ofMillis(timeout), forwardHeartbeats));
        }
        // the heartbeat setup is done, no need to stay in the pipeline
        ctx.pipeline().remove(this);
        ctx.writeAndFlush(heartbeatResponse(request.id()));

        ReferenceCountUtil.release(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    private long heartbeatTimeout(final byte[] payload) {
      final var buffer = new UnsafeBuffer(payload);
      try {
        requestDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        // it's a u32 on the wire
        return requestDecoder.heartbeatTimeout();
      } catch (final IllegalStateException e) {
        log.warn("Unable to decode heartbeat request from client, heartbeats are disabled.", e);
        return 0;
      }
    }

    private ProtocolReply heartbeatResponse(final long id) {
      final var bytes = allocateResponseBuffer();
      final var buffer = new UnsafeBuffer(bytes);
      responseEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
      responseEncoder.heartbeatEnabled(BooleanType.TRUE);
      return new ProtocolReply(id, bytes, Status.OK);
    }

    private byte[] allocateResponseBuffer() {
      return new byte
          [MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatSetupResponseEncoder.BLOCK_LENGTH];
    }
  }
}
