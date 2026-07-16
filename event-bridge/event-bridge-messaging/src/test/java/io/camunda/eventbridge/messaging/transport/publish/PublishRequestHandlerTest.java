/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.publish;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.InboundPayload;
import io.camunda.eventbridge.messaging.flowcontrol.FlowControl;
import io.camunda.eventbridge.messaging.publish.InboundQueue;
import io.camunda.eventbridge.messaging.stream.EventStreamWriter;
import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.ExecutePublishRequestDecoder;
import io.camunda.eventbridge.protocol.ExecutePublishResponseDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.PublishResponseStatus;
import io.camunda.eventbridge.protocol.RejectionReason;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the payload ownership contract of the publish handler: the payload is released exactly
 * once on every rejection path, and ownership transfers to the pipeline on acceptance.
 */
final class PublishRequestHandlerTest {

  private static final int PARTITION_ID = 1;

  private final PublishRequestCorrelator correlator =
      new PublishRequestCorrelator(new AtomicInteger()::incrementAndGet);

  @Test
  void shouldReleasePayloadWhenRequestIsTooShort() {
    // given
    final var handler = handlerWith(admittingFlowControl(), CleanupPolicy.DELETE);
    final var payload = new TrackingPayload(new byte[4]);

    // when
    final var response = handler.handleInbound(payload);

    // then
    assertThat(response).isCompletedExceptionally();
    assertThat(payload.releases.get()).isEqualTo(1);
  }

  @Test
  void shouldReleasePayloadOnBackpressure() {
    // given
    final var handler = handlerWith(rejectingFlowControl(), CleanupPolicy.DELETE);
    final var payload = new TrackingPayload(validRequestBytes());

    // when
    final var response = handler.handleInbound(payload);

    // then
    assertThat(response).isCompleted();
    assertThat(payload.releases.get()).isEqualTo(1);
  }

  @Test
  void shouldTransferOwnershipToThePipelineOnAcceptance() {
    // given
    final var handler = handlerWith(admittingFlowControl(), CleanupPolicy.DELETE);
    final var payload = new TrackingPayload(validRequestBytes());

    // when
    final var response = handler.handleInbound(payload);

    // then: the request is in flight, the payload stays alive for the appender
    assertThat(response).isNotDone();
    assertThat(payload.releases.get()).isZero();
  }

  @Test
  void shouldRejectUnkeyedBatchOnCompactedTopic() {
    // given
    final var handler = handlerWith(admittingFlowControl(), CleanupPolicy.COMPACT);
    final var payload = new TrackingPayload(validRequestBytes());

    // when
    final var response = handler.handleInbound(payload);

    // then
    assertThat(response).isCompleted();
    assertThat(payload.releases.get()).isEqualTo(1);
    final var decoded = decodeResponse(response.join());
    assertThat(decoded.status).isEqualTo(PublishResponseStatus.ERROR);
    assertThat(decoded.rejectionReason).isEqualTo(RejectionReason.UNKEYED_ON_COMPACTED_TOPIC);
  }

  @Test
  void shouldAcceptKeyedBatchOnCompactedTopic() {
    // given
    final var handler = handlerWith(admittingFlowControl(), CleanupPolicy.COMPACT);
    final var payload = new TrackingPayload(keyedRequestBytes());

    // when
    final var response = handler.handleInbound(payload);

    // then: the request is in flight, the payload stays alive for the appender
    assertThat(response).isNotDone();
    assertThat(payload.releases.get()).isZero();
  }

  @Test
  void shouldAcceptKeyedBatchOnDeleteTopic() {
    // given: keyed publishes to a DELETE topic remain allowed (KEYED is simply unused there)
    final var handler = handlerWith(admittingFlowControl(), CleanupPolicy.DELETE);
    final var payload = new TrackingPayload(keyedRequestBytes());

    // when
    final var response = handler.handleInbound(payload);

    // then
    assertThat(response).isNotDone();
    assertThat(payload.releases.get()).isZero();
  }

  private PublishRequestHandler handlerWith(
      final FlowControl flowControl, final CleanupPolicy cleanupPolicy) {
    final var inbound = new InboundQueue(16, flowControl);
    final var writer = new EventStreamWriter(inbound, () -> {});
    return new PublishRequestHandler(PARTITION_ID, writer, correlator, cleanupPolicy);
  }

  private static int batchOffset() {
    return MessageHeaderDecoder.ENCODED_LENGTH
        + ExecutePublishRequestDecoder.BLOCK_LENGTH
        + ExecutePublishRequestDecoder.entryBatchHeaderLength();
  }

  private static byte[] validRequestBytes() {
    // header (zeroed SBE frame is irrelevant to the handler) + a minimal batch region whose
    // entry count is read by the pipeline
    return new byte[256];
  }

  /** {@link #validRequestBytes()} with the batch's KEYED attribute bit set. */
  private static byte[] keyedRequestBytes() {
    final byte[] bytes = validRequestBytes();
    final var buffer = new UnsafeBuffer(bytes);
    buffer.putInt(batchOffset() + EventBridgeBatch.ATTRIBUTES_OFFSET, EventBridgeBatch.KEYED_MASK);
    return bytes;
  }

  private static DecodedResponse decodeResponse(final byte[] responseBytes) {
    final var headerDecoder = new MessageHeaderDecoder();
    final var bodyDecoder = new ExecutePublishResponseDecoder();
    final var buffer = new UnsafeBuffer(responseBytes);
    bodyDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
    return new DecodedResponse(bodyDecoder.status(), bodyDecoder.rejectionReason());
  }

  private record DecodedResponse(PublishResponseStatus status, RejectionReason rejectionReason) {}

  private static FlowControl admittingFlowControl() {
    return new FlowControl() {
      @Override
      public boolean tryAcquire(final int permits) {
        return true;
      }

      @Override
      public void release(final int permits) {}
    };
  }

  private static FlowControl rejectingFlowControl() {
    return new FlowControl() {
      @Override
      public boolean tryAcquire(final int permits) {
        return false;
      }

      @Override
      public void release(final int permits) {}
    };
  }

  private static final class TrackingPayload implements InboundPayload {

    private final UnsafeBuffer view;
    private final byte[] bytes;
    private final AtomicInteger releases = new AtomicInteger();

    private TrackingPayload(final byte[] bytes) {
      this.bytes = bytes;
      view = new UnsafeBuffer(bytes);
    }

    @Override
    public DirectBuffer view() {
      return view;
    }

    @Override
    public int length() {
      return bytes.length;
    }

    @Override
    public byte[] toBytes() {
      return bytes;
    }

    @Override
    public void encode(final ByteBuf buffer, final List<Object> out) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void release() {
      releases.incrementAndGet();
    }
  }
}
