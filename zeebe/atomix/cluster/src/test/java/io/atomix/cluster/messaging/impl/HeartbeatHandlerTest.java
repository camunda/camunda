/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.impl.HeartbeatHandler.LegacyServerHeartbeatHandler;
import io.atomix.cluster.messaging.impl.ProtocolReply.Status;
import io.atomix.utils.net.Address;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HeartbeatHandlerTest {

  private final List<Object> forwardedMessages = new ArrayList<>();
  private final EmbeddedChannel channel =
      new EmbeddedChannel(
          new LegacyServerHeartbeatHandler(),
          new SimpleChannelInboundHandler<Object>() {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
              forwardedMessages.add(msg);
            }
          });

  @Test
  void shouldNotReplyToLegacyHeartbeatRequest() {
    // given
    final var request =
        new ProtocolRequest(
            42L, Address.from(26602), HeartbeatHandler.HEARTBEAT_SUBJECT, new byte[0]);

    // when
    channel.writeInbound(request);

    // then
    assertThat((Object) channel.readOutbound()).isNull();
  }

  @Test
  void shouldConsumeALegacyHeartbeatRequestDecodedFromTheWire() {
    // given
    final var request =
        new ProtocolRequest(
            42L, Address.from(26602), HeartbeatHandler.HEARTBEAT_SUBJECT, new byte[0]);
    final var protocol = ProtocolVersion.latest().createProtocol(Address.from(26602));
    final var encoderChannel = new EmbeddedChannel(protocol.newEncoder());
    encoderChannel.writeOutbound(request);
    final ByteBuf encoded = encoderChannel.readOutbound();

    final var wireChannel =
        new EmbeddedChannel(
            protocol.newDecoder(),
            new LegacyServerHeartbeatHandler(),
            new SimpleChannelInboundHandler<Object>() {
              @Override
              protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
                forwardedMessages.add(msg);
              }
            });

    // when
    wireChannel.writeInbound(encoded);

    // then
    assertThat(forwardedMessages).isEmpty();
    assertThat((Object) wireChannel.readOutbound()).isNull();
    assertThat(wireChannel.isOpen()).isTrue();
  }

  @Test
  void shouldNotForwardLegacyHeartbeatRequestToNextHandler() {
    // given
    final var request =
        new ProtocolRequest(
            42L, Address.from(26602), HeartbeatHandler.HEARTBEAT_SUBJECT, new byte[0]);

    // when
    channel.writeInbound(request);

    // then
    assertThat(forwardedMessages).isEmpty();
  }

  @Test
  void shouldKeepChannelOpenAfterLegacyHeartbeatRequest() {
    // given
    final var request =
        new ProtocolRequest(
            42L, Address.from(26602), HeartbeatHandler.HEARTBEAT_SUBJECT, new byte[0]);

    // when
    channel.writeInbound(request);

    // then
    assertThat(channel.isOpen()).isTrue();
  }

  @Test
  void shouldForwardRequestsWithOtherSubjectsUnchanged() {
    // given
    final var request = new ProtocolRequest(1L, Address.from(26602), "some-subject", new byte[0]);

    // when
    channel.writeInbound(request);

    // then
    assertThat(forwardedMessages).containsExactly(request);
    assertThat((Object) channel.readOutbound()).isNull();
  }

  @Test
  void shouldForwardProtocolRepliesUnchanged() {
    // given
    final var reply = new ProtocolReply(1L, new byte[0], Status.OK);

    // when
    channel.writeInbound(reply);

    // then
    assertThat(forwardedMessages).containsExactly(reply);
    assertThat((Object) channel.readOutbound()).isNull();
  }
}
