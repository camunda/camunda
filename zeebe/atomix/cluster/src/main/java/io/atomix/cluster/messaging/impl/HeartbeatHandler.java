/* * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under * one or more contributor license agreements. See the NOTICE file distributed * with this work for additional information regarding copyright ownership. * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.HeartbeatRequestEncoder;
import io.atomix.cluster.messaging.HeartbeatResponseDecoder;
import io.atomix.cluster.messaging.HeartbeatResponseEncoder;
import io.atomix.cluster.messaging.MessageHeaderDecoder;
import io.atomix.cluster.messaging.MessageHeaderEncoder;
import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/**
 * HeartbeatHandler allows to add two-way heartbeats to a connection. To do so, both parties must
 * add as a first element of their channel pipeline an {@link
 * io.netty.handler.timeout.IdleStateHandler} with the same timeout configuration (as different
 * timeout may lead to false positives).
 *
 * <p>The client must then add a {@link HeartbeatHandler.Client} handler to its pipeline and the
 * server a {@link HeartbeatHandler.Server}.
 *
 * <p>
 *
 * <ul>
 *   <li>Heartbeats are initiated by the client. The client sends a heartbeat if he hasn't written
 *       anything in the channel for a while ({@link io.netty.handler.timeout.IdleStateHandler} will
 *       emit an event when such condition occurs
 *   <li>The server, upon receiving a heartbeat (in a {@link ProtocolRequest} message), will reply
 *       with a heartbeat response (in a {@link ProtocolReply})
 *   <li>If the client does not receive any message for a while ({@link
 *       io.netty.handler.timeout.IdleStateHandler} will emit an event when such condition occurs)
 *       it will close the connection
 *   <li>When the server has detected that it did not receive anything for a while ({@link
 *       io.netty.handler.timeout.IdleStateHandler} will emit such event), it will close the
 *       connection if he has detected at least one heartbeat. This condition allows the server to
 *       not prematurely close the connection if the client's does not send any heartbeats (i.e. was
 *       an older version).
 * </ul>
 */
abstract sealed class HeartbeatHandler extends ChannelDuplexHandler {
  // DO NOT CHANGE: changing the subject is a backward INCOMPATIBLE change.
  static final String HEARTBEAT_SUBJECT = "internal-heartbeat";
  static final byte[] EMPTY_HEARTBEAT_PAYLOAD = new byte[0];
  static final String IDLE_STATE_HANDLER_NAME = "idle";
  static final String HEARTBEAT_HANDLER_NAME = "heartbeat";

  private static final int HEARTBEAT_REQUEST_SIZE =
      MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatRequestEncoder.BLOCK_LENGTH;
  private static final int HEARTBEAT_RESPONSE_SIZE =
      MessageHeaderEncoder.ENCODED_LENGTH + HeartbeatResponseEncoder.BLOCK_LENGTH;
  protected final Logger log;
  protected final boolean forwardHeartbeats;

  protected HeartbeatHandler(final Logger log, final boolean forwardHeartbeats) {
    this.log = log;
    this.forwardHeartbeats = forwardHeartbeats;
  }

  static final class Server extends HeartbeatHandler {
    private final Duration heartbeatTimeout;
    private final boolean sendHeartbeatPayload;
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final HeartbeatResponseEncoder heartbeatResponseEncoder =
        new HeartbeatResponseEncoder();

    Server(
        final Logger log,
        final Duration heartbeatTimeout,
        final boolean fireHeartbeats,
        final boolean sendHeartbeatPayload) {
      super(log, fireHeartbeats);
      this.heartbeatTimeout = heartbeatTimeout;
      this.sendHeartbeatPayload = sendHeartbeatPayload;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
      if (ctx.channel().isActive()) {
        ctx.pipeline()
            .addFirst(
                IDLE_STATE_HANDLER_NAME,
                // server is only interested in readIdleTime
                new IdleStateHandler(heartbeatTimeout.toMillis(), 0, 0, TimeUnit.MILLISECONDS));
      }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      boolean heartbeatReceived = false;
      if (msg instanceof final ProtocolRequest request
          && request.subject().equals(HEARTBEAT_SUBJECT)) {
        heartbeatReceived = true;
        ctx.writeAndFlush(createHeartbeat(request.id()));
      }

      // Pass the message to the next handler.
      // Handlers downstream can receive heartbeats as well
      if (!heartbeatReceived || forwardHeartbeats) {
        ctx.fireChannelRead(msg);
      } else {
        ReferenceCountUtil.release(msg);
      }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (!(evt instanceof final IdleStateEvent idleStateEvent)) {
        return;
      }
      if (idleStateEvent.state() == IdleState.READER_IDLE) {
        // don't close the connection if we never receive any heartbeat
        log.warn(
            "Connection {} on the server timed out after idling with no heartbeats from the client, closing channel.",
            ctx.channel());
        ctx.close();
      }
    }

    private ProtocolMessage createHeartbeat(final long id) {
      final byte[] payload =
          sendHeartbeatPayload ? writeHeartbeatResponse() : EMPTY_HEARTBEAT_PAYLOAD;
      log.trace("Heartbeat response payload for req id={} is {}", id, payload);
      return new ProtocolReply(id, payload, Status.OK);
    }

    private byte[] writeHeartbeatResponse() {
      final var heartbeatResponseBuffer = new UnsafeBuffer(new byte[HEARTBEAT_RESPONSE_SIZE]);
      heartbeatResponseEncoder.wrapAndApplyHeader(heartbeatResponseBuffer, 0, messageHeaderEncoder);
      heartbeatResponseEncoder.receivedAt(System.currentTimeMillis());
      return heartbeatResponseBuffer.byteArray();
    }
  }

  static final class Client extends HeartbeatHandler {
    private final LongHashSet outstandingHeartbeats = new LongHashSet();
    private final AtomicLong messageIdGenerator;
    private final Address advertisedAddress;
    private final Duration heartbeatTimeout;
    private final Duration heartbeatInterval;
    private final boolean sendHeartbeatPayload;
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final HeartbeatRequestEncoder heartbeatRequestEncoder = new HeartbeatRequestEncoder();

    Client(
        final Logger log,
        final AtomicLong messageIdGenerator,
        final Address advertisedAddress,
        final Duration heartbeatTimeout,
        final Duration heartbeatInterval,
        final boolean fireHeartbeats,
        final boolean sendHeartbeatPayload) {
      super(log, fireHeartbeats);
      this.messageIdGenerator = messageIdGenerator;
      this.advertisedAddress = advertisedAddress;
      this.heartbeatTimeout = heartbeatTimeout;
      this.heartbeatInterval = heartbeatInterval;
      this.sendHeartbeatPayload = sendHeartbeatPayload;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
      if (ctx.channel().isActive()) {
        ctx.pipeline()
            .addFirst(
                IDLE_STATE_HANDLER_NAME,
                new IdleStateHandler(
                    heartbeatTimeout.toMillis(),
                    heartbeatInterval.toMillis(),
                    0,
                    TimeUnit.MILLISECONDS));
      }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      boolean heartbeatReceived = forwardHeartbeats;
      if (msg instanceof final ProtocolReply reply) {
        var isHeartbeat = false;
        if (sendHeartbeatPayload && reply.payload().length == HEARTBEAT_RESPONSE_SIZE) {
          messageHeaderDecoder.wrap(new UnsafeBuffer(reply.payload()), 0);
          isHeartbeat = messageHeaderDecoder.schemaId() == HeartbeatResponseDecoder.SCHEMA_ID;
        } else {
          isHeartbeat = reply.payload().length == 0 && outstandingHeartbeats.contains(reply.id());
        }
        // remove all heartbeats sent before (and equal) to the last ProtocolReply received.
        // Note that all ProtocolReply messages (not just heartbeat messages) can indicate that
        // the channel is still open and transmitting.
        // If we remove from the set only the last message received, the size of
        // oustandingHeartbeats will increase for every missed
        // heartbeat and the memory will be freed only when the channel is closed.
        outstandingHeartbeats.removeIfLong(id -> id <= reply.id());
        if (isHeartbeat) {
          // there's no subject in ProtocolReply, so we rely on oustandingHeartbeats to see if it
          // was a reply to a heartbeat
          heartbeatReceived = true;

          if (reply.status() != Status.OK) {
            log.warn("Received a Heartbeat response with status {}", reply.status());
          }
        }
      }

      // Pass the message to the next handler, even though it's a heartbeat.
      if (!heartbeatReceived || forwardHeartbeats) {
        ctx.fireChannelRead(msg);
      } else {
        ReferenceCountUtil.release(msg);
      }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (!(evt instanceof final IdleStateEvent idleStateEvent)) {
        return;
      }
      switch (idleStateEvent.state()) {
        case READER_IDLE -> {
          // don't close the connection if we haven't sent any heartbeat yet
          if (!outstandingHeartbeats.isEmpty()) {
            log.warn(
                "Connection {} timed out on the client after not receiving a heartbeat response from the server in {}({} heartbeats pending) closing channel",
                ctx.channel(),
                heartbeatTimeout,
                outstandingHeartbeats.size());
            outstandingHeartbeats.clear();
            ctx.close();
          }
        }
        case WRITER_IDLE -> {
          ctx.writeAndFlush(createOutstandingHeartbeat());
        }
        default -> {}
      }
    }

    /**
     * Creates a heartbeat and insert its id in the map
     *
     * @return a heartbeat
     */
    private ProtocolRequest createOutstandingHeartbeat() {
      final byte[] payload =
          sendHeartbeatPayload ? writeHeartbeatRequest() : EMPTY_HEARTBEAT_PAYLOAD;
      final var heartbeat =
          new ProtocolRequest(
              messageIdGenerator.incrementAndGet(), advertisedAddress, HEARTBEAT_SUBJECT, payload);
      outstandingHeartbeats.add(heartbeat.id());
      log.debug("Payload for heartbeat request with id={} is {}", heartbeat.id(), heartbeat);
      return heartbeat;
    }

    private byte[] writeHeartbeatRequest() {
      final UnsafeBuffer heartbeatRequestBuffer =
          new UnsafeBuffer(new byte[HEARTBEAT_REQUEST_SIZE]);
      heartbeatRequestEncoder.wrapAndApplyHeader(heartbeatRequestBuffer, 0, messageHeaderEncoder);
      heartbeatRequestEncoder.sentAt(System.currentTimeMillis());
      return heartbeatRequestBuffer.byteArray();
    }
  }
}
