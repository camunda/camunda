/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.BlockStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.UUIDEncoder;
import io.camunda.zeebe.transport.stream.impl.messages.UnblockStreamRequest;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

final class RemoteStreamApiHandlerTest {
  private static final UnsafeBuffer SERIALIZED_METADATA =
      new UnsafeBuffer(ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(0, 1));

  private final RemoteStreamRegistry<TestMetadata> registry =
      new RemoteStreamRegistry<>(RemoteStreamMetrics.noop());
  private final RemoteStreamApiHandler<TestMetadata> server =
      new RemoteStreamApiHandler<>(
          registry,
          buffer -> {
            final var data = new TestMetadata();
            data.wrap(buffer, 0, buffer.capacity());
            return data;
          });

  @Test
  void shouldNotAddOnMetadataReadError() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var request =
        new AddStreamRequest()
            .streamId(UUID.randomUUID())
            .streamType(streamType)
            .metadata(new UnsafeBuffer()); // an empty buffer will cause the read to fail
    final var sender = MemberId.anonymous();

    // when
    final var response = server.add(sender, request);

    // then
    assertThat(response)
        .isInstanceOf(ErrorResponse.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.MALFORMED);
    assertThat(registry.get(streamType)).isEmpty();
  }

  @Test
  void shouldNotAddWithEmptyStreamType() {
    // given
    final var request =
        new AddStreamRequest().streamId(UUID.randomUUID()).metadata(SERIALIZED_METADATA);
    final var sender = MemberId.anonymous();

    // when
    final var response = server.add(sender, request);

    // then
    assertThat(response)
        .isInstanceOf(ErrorResponse.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.INVALID);
    assertThat(registry.list()).isEmpty();
  }

  @Test
  void shouldNotAddWithNullStreamId() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("streamType"));
    final var request = new AddStreamRequest().streamType(streamType).metadata(SERIALIZED_METADATA);
    final var sender = MemberId.anonymous();

    // when
    final var response = server.add(sender, request);

    // then
    assertThat(response)
        .isInstanceOf(ErrorResponse.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.INVALID);
    assertThat(registry.get(streamType)).isEmpty();
  }

  @Test
  void shouldNotAddWithNilStreamId() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("streamType"));
    final var nilUuid = new UUID(UUIDEncoder.highNullValue(), UUIDEncoder.lowNullValue());
    final var request =
        new AddStreamRequest()
            .streamType(streamType)
            .metadata(SERIALIZED_METADATA)
            .streamId(nilUuid);
    final var sender = MemberId.anonymous();

    // when
    final var response = server.add(sender, request);

    // then
    assertThat(response)
        .isInstanceOf(ErrorResponse.class)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.INVALID);
    assertThat(registry.get(streamType)).isEmpty();
  }

  @Test
  void shouldAddStream() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = UUID.randomUUID();
    final var request =
        new AddStreamRequest()
            .streamId(streamId)
            .streamType(streamType)
            .metadata(SERIALIZED_METADATA);
    final var sender = MemberId.anonymous();

    // when
    server.add(sender, request);

    // then
    final var consumers = registry.get(streamType);
    assertThat(consumers).hasSize(1);
    final var stream = consumers.stream().findFirst().orElseThrow();
    assertThat(stream.logicalId())
        .extracting(LogicalId::streamType, c -> c.metadata().version)
        .containsExactly(streamType, 1);
    assertThat(stream.streamConsumers())
        .hasSize(1)
        .first()
        .extracting(c -> c.id().streamId(), c -> c.id().receiver())
        .containsExactly(streamId, sender);
  }

  @Test
  void shouldRemoveStream() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = UUID.randomUUID();
    final var sender = MemberId.anonymous();
    final var request = new RemoveStreamRequest().streamId(streamId);
    registry.add(streamType, streamId, sender, new TestMetadata());

    // when
    server.remove(sender, request);

    // then
    final var consumers = registry.get(streamType);
    assertThat(consumers).isEmpty();
  }

  @Test
  void shouldRemoveAllStream() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var sender = MemberId.anonymous();
    registry.add(streamType, UUID.randomUUID(), sender, new TestMetadata());

    // when
    server.removeAll(sender);

    // then
    final var consumers = registry.get(streamType);
    assertThat(consumers).isEmpty();
  }

  @Test
  void shouldNotBlockStreamIfNoId() {
    // given
    final var sender = MemberId.anonymous();
    final var request = new BlockStreamRequest();

    // when
    final var response = server.block(sender, request);

    // then
    assertThat(response)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .returns(ErrorCode.INVALID, ErrorResponse::code);
  }

  @Test
  void shouldBlockStream() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = UUID.randomUUID();
    final var sender = MemberId.anonymous();
    final var request = new BlockStreamRequest().streamId(streamId);
    registry.add(streamType, streamId, sender, new TestMetadata());
    final var stream = getStreamById(streamId);

    // when
    server.block(sender, request);

    // then
    assertThat(stream.isBlocked()).isTrue();
  }

  @Test
  void shouldUnblockStream() {
    // given
    final var streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
    final var streamId = UUID.randomUUID();
    final var sender = MemberId.anonymous();
    final var request = new UnblockStreamRequest().streamId(streamId);
    registry.add(streamType, streamId, sender, new TestMetadata());
    final var stream = getStreamById(streamId);

    // when
    server.unblock(sender, request);

    // then
    assertThat(stream.isBlocked()).isFalse();
  }

  @Test
  void shouldNotUnblockStreamIfNoId() {
    // given
    final var sender = MemberId.anonymous();
    final var request = new UnblockStreamRequest();

    // when
    final var response = server.unblock(sender, request);

    // then
    assertThat(response)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .returns(ErrorCode.INVALID, ErrorResponse::code);
  }

  private StreamConsumer<TestMetadata> getStreamById(final UUID streamId) {
    return registry.list().stream()
        .map(AggregatedRemoteStream::streamConsumers)
        .flatMap(List::stream)
        .filter(s -> s.id().streamId().equals(streamId))
        .findFirst()
        .orElseThrow();
  }

  private static final class TestMetadata implements BufferReader {
    private int version;

    private TestMetadata() {}

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      version = buffer.getInt(0, ByteOrder.nativeOrder());
    }
  }
}
