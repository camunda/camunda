/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.messaging.ByteArrayPayload;
import io.atomix.utils.net.Address;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.IllegalReferenceCountException;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pipes encoded protocol messages back through the decoder to verify the encoder's output stays
 * parseable — in particular across the payload wrap-vs-copy threshold in ByteArrayPayload.
 */
@Execution(ExecutionMode.CONCURRENT)
final class MessagingProtocolV2RoundtripTest {

  private final Address address = Address.from("localhost", 5000);

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 100, 4095, 4096, 4097, 1024 * 1024})
  void shouldRoundtripRequestAcrossWrapThreshold(final int payloadSize) {
    // given
    final byte[] payload = randomBytes(payloadSize);
    final var request = new ProtocolRequest(17, address, "test-subject", payload);

    // when
    final ProtocolRequest decoded = roundtrip(request);

    // then
    assertThat(decoded.id()).isEqualTo(17);
    assertThat(decoded.subject()).isEqualTo("test-subject");
    assertThat(decoded.payloadAsBytes()).isEqualTo(payload);
    decoded.payload().release();
  }

  @Test
  void shouldSliceLargeRequestsAndCopySmallOnes() {
    // given
    final var small = new ProtocolRequest(1, address, "s", randomBytes(16));
    final var large =
        new ProtocolRequest(2, address, "l", randomBytes(MessageDecoderV2.SLICE_THRESHOLD));

    // when
    final ProtocolRequest decodedSmall = roundtrip(small);
    final ProtocolRequest decodedLarge = roundtrip(large);

    // then: small requests stay heap-backed, large ones reference the receive buffer
    assertThat(decodedSmall.payload()).isInstanceOf(ByteArrayPayload.class);
    assertThat(decodedLarge.payload()).isInstanceOf(NettyInboundPayload.class);

    // reading in place matches the materialized bytes
    final var inbound = (NettyInboundPayload) decodedLarge.payload();
    final byte[] viewed = new byte[inbound.length()];
    inbound.view().getBytes(0, viewed, 0, viewed.length);
    assertThat(viewed).isEqualTo(decodedLarge.payloadAsBytes());

    // the slice is reference-counted: releasing it once frees it
    inbound.release();
    assertThatThrownBy(inbound::release).isInstanceOf(IllegalReferenceCountException.class);
  }

  @Test
  void shouldKeepRepliesHeapBacked() {
    // given: replies are consumed as byte arrays on the client dispatch path and must not require
    // an explicit release
    final var reply =
        new ProtocolReply(
            5, randomBytes(MessageDecoderV2.SLICE_THRESHOLD * 2), ProtocolReply.Status.OK);

    // when
    final ProtocolReply decoded = roundtrip(reply);

    // then
    assertThat(decoded.payload()).isInstanceOf(ByteArrayPayload.class);
  }

  @Test
  void shouldReleaseAPendingSliceWhenTheChannelClosesMidFrame() {
    // given: a large request whose content was decoded but whose subject trailer never arrives
    final var encodeChannel = new EmbeddedChannel(new MessageEncoderV2(address));
    final var decodeChannel = new EmbeddedChannel(new MessageDecoderV2());
    encodeChannel.writeOutbound(
        new ProtocolRequest(7, address, "sub", randomBytes(MessageDecoderV2.SLICE_THRESHOLD)));

    final var frame = decodeChannel.alloc().buffer();
    Object outbound;
    while ((outbound = encodeChannel.readOutbound()) != null) {
      if (outbound instanceof final ByteBuf buf) {
        frame.writeBytes(buf);
        buf.release();
      }
    }
    final var truncated = frame.readRetainedSlice(frame.readableBytes() - 3);
    frame.release();

    // when: the truncated frame arrives and the channel closes mid-message
    decodeChannel.writeInbound(truncated);
    assertThat(decodeChannel.<Object>readInbound()).isNull();
    decodeChannel.finishAndReleaseAll();

    // then: the retained content slice was released along with the channel
    assertThat(truncated.refCnt()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 100, 4096, 1024 * 1024})
  void shouldRoundtripReplyAcrossWrapThreshold(final int payloadSize) {
    // given
    final byte[] payload = randomBytes(payloadSize);
    final var reply = new ProtocolReply(23, payload, ProtocolReply.Status.OK);

    // when
    final ProtocolReply decoded = roundtrip(reply);

    // then
    assertThat(decoded.id()).isEqualTo(23);
    assertThat(decoded.status()).isEqualTo(ProtocolReply.Status.OK);
    assertThat(decoded.payloadAsBytes()).isEqualTo(payload);
  }

  @Test
  void shouldRoundtripInterleavedSmallAndLargePayloads() {
    // given
    final var encodeChannel = new EmbeddedChannel(new MessageEncoderV2(address));
    final var decodeChannel = new EmbeddedChannel(new MessageDecoderV2());
    final byte[] small = randomBytes(16);
    final byte[] large = randomBytes(64 * 1024);

    // when
    encodeChannel.writeOutbound(new ProtocolRequest(1, address, "s", small));
    encodeChannel.writeOutbound(new ProtocolRequest(2, address, "l", large));
    encodeChannel.writeOutbound(new ProtocolRequest(3, address, "s", small));
    pipe(encodeChannel, decodeChannel);

    // then
    final ProtocolRequest first = decodeChannel.readInbound();
    final ProtocolRequest second = decodeChannel.readInbound();
    final ProtocolRequest third = decodeChannel.readInbound();
    assertThat(first.payloadAsBytes()).isEqualTo(small);
    assertThat(second.payloadAsBytes()).isEqualTo(large);
    assertThat(third.payloadAsBytes()).isEqualTo(small);
  }

  private <M extends ProtocolMessage> M roundtrip(final M message) {
    final var encodeChannel = new EmbeddedChannel(new MessageEncoderV2(address));
    final var decodeChannel = new EmbeddedChannel(new MessageDecoderV2());
    encodeChannel.writeOutbound(message);
    pipe(encodeChannel, decodeChannel);
    final M decoded = decodeChannel.readInbound();
    assertThat(decoded).isNotNull();
    return decoded;
  }

  private static void pipe(final EmbeddedChannel from, final EmbeddedChannel to) {
    Object outbound;
    while ((outbound = from.readOutbound()) != null) {
      if (outbound instanceof final ByteBuf buf) {
        to.writeInbound(buf);
      }
    }
  }

  private static byte[] randomBytes(final int size) {
    final byte[] bytes = new byte[size];
    ThreadLocalRandom.current().nextBytes(bytes);
    return bytes;
  }
}
