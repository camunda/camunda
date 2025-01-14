/* * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under * one or more contributor license agreements. See the NOTICE file distributed * with this work for additional information regarding copyright ownership. * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.collections.LongHashSet;
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
  static final byte[] HEARTBEAT_PAYLOAD = new byte[0];
  protected final Logger log;

  protected HeartbeatHandler(final Logger log) {
    this.log = log;
  }

  static final class Server extends HeartbeatHandler {
    boolean receivedHeartbeat = false;

    Server(final Logger log) {
      super(log);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      if (msg instanceof final ProtocolRequest request
          && request.subject().equals(HEARTBEAT_SUBJECT)) {
        receivedHeartbeat = true;
        ctx.writeAndFlush(createHeartbeat(request.id()));
        if (!Arrays.equals(request.payload(), HEARTBEAT_PAYLOAD)) {
          log.warn(
              "Received unexpected heartbeat payload from {}, perhaps the message subject {} is accidentally reused",
              request.sender(),
              HEARTBEAT_SUBJECT);
        }
      }

      // Pass the message to the next handler.
      // Handlers downstream can receive heartbeats as well
      ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (!(evt instanceof final IdleStateEvent idleStateEvent)) {
        return;
      }
      if (idleStateEvent.state() == IdleState.READER_IDLE) {
        // don't close the connection if we never receive any heartbeat
        if (receivedHeartbeat) {
          log.warn(
              "Connection {} on the server timed out after idling with no heartbeats from the client, closing channel.",
              ctx.channel());
          ctx.close();
        }
      }
    }

    private ProtocolMessage createHeartbeat(final long id) {
      return new ProtocolReply(id, HEARTBEAT_PAYLOAD, Status.OK);
    }
  }

  static final class Client extends HeartbeatHandler {
    private final LongHashSet outstandingHeartbeats = new LongHashSet();
    private final AtomicLong messageIdGenerator;
    private final Address advertisedAddress;
    private final Duration heartbeatTimeout;
    private boolean receivedAnyHeartbeats = false;

    Client(
        final Logger log,
        final AtomicLong messageIdGenerator,
        final Address advertisedAddress,
        final Duration heartbeatTimeout) {
      super(log);
      this.messageIdGenerator = messageIdGenerator;
      this.advertisedAddress = advertisedAddress;
      this.heartbeatTimeout = heartbeatTimeout;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      if (msg instanceof final ProtocolReply reply) {
        if (outstandingHeartbeats.contains(reply.id())) {
          // there's no subject in ProtocolReply, so we rely on oustandingHeartbeats to see if it
          // was a reply to a heartbeat
          receivedAnyHeartbeats = true;
        }
        // remove all heartbeats sent before (and equal) to the last ProtocolReply received.
        // Note that all ProtocolReply messages (not just heartbeat messages) can indicate that
        // the channel is still open and transmitting.
        // If we remove from the set only the last message received, the size of
        // oustandingHeartbeats will increase for every missed
        // heartbeat and the memory will be freed only when the channel is closed.
        outstandingHeartbeats.removeIfLong(id -> id <= reply.id());

        if (reply.status() != Status.OK) {
          log.warn("Received a Heartbeat response with status {}", reply.status());
        } else if (!Arrays.equals(reply.payload(), HEARTBEAT_PAYLOAD)) {
          log.warn(
              "Received unexpected heartbeat payload, perhaps the message subject {} is accidentally reused: {}",
              HEARTBEAT_SUBJECT,
              reply);
        }
      }

      // Pass the message to the next handler, even though it's a heartbeat.
      ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (!(evt instanceof final IdleStateEvent idleStateEvent)) {
        return;
      }
      switch (idleStateEvent.state()) {
        case READER_IDLE -> {
          // don't close the connection if we haven't sent any heartbeat yet
          if (receivedAnyHeartbeats && !outstandingHeartbeats.isEmpty()) {
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
          ctx.writeAndFlush(createHeartbeat());
        }
        default -> {}
      }
    }

    private ProtocolRequest createHeartbeat() {
      return new ProtocolRequest(
          messageIdGenerator.incrementAndGet(),
          advertisedAddress,
          HEARTBEAT_SUBJECT,
          HEARTBEAT_PAYLOAD);
    }
  }
}
