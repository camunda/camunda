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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.collections.LongHashSet;
import org.slf4j.Logger;

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

      // Pass the message to the next handler, even though it's a heartbeat.
      ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
      if (!(evt instanceof final IdleStateEvent idleStateEvent)) {
        return;
      }
      if (idleStateEvent.state() == IdleState.READER_IDLE) {
        // don't close the connection if we did not receive any heartbeat
        if (receivedHeartbeat) {
          log.warn("Connection {} timed out on the server, closing channel", ctx.channel());
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

    Client(final Logger log, final AtomicLong messageIdGenerator, final Address advertisedAddress) {
      super(log);
      this.messageIdGenerator = messageIdGenerator;
      this.advertisedAddress = advertisedAddress;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
      if (msg instanceof final ProtocolReply reply && outstandingHeartbeats.contains(reply.id())) {
        // remove all heartbeats sent before the last update
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
          // don't close the connection if we did not send any heartbeat
          if (!outstandingHeartbeats.isEmpty()) {
            log.warn("Connection {} timed out on the client, closing channel", ctx.channel());
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
