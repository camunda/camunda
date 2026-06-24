/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.concurrent.ExecutionException;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the bridge routes a successful reply to the caller and fails it on a command rejection.
 */
final class BridgingCommandResponseWriterTest {

  private final RequestResponseBridge bridge = new RequestResponseBridge();
  private final BridgingCommandResponseWriter writer = new BridgingCommandResponseWriter(bridge);

  @Test
  void shouldCompleteRequestWithSerializedResponseValue() throws Exception {
    // given a pending request and a success (EVENT) reply
    final var registration = bridge.register();
    final var payload = new byte[] {1, 2, 3};

    // when the platform flushes the response
    writer
        .recordType(RecordType.EVENT)
        .valueWriter(bufferWriter(payload))
        .tryWriteResponse(1, registration.requestId());

    // then the caller gets the serialized value bytes
    assertThat(registration.response()).isCompleted();
    assertThat(registration.response().get()).isEqualTo(payload);
  }

  @Test
  void shouldFailRequestWithRejectionOnCommandRejection() {
    // given a pending request and a COMMAND_REJECTION reply
    final var registration = bridge.register();

    // when the platform flushes the rejection
    writer
        .recordType(RecordType.COMMAND_REJECTION)
        .rejectionType(RejectionType.INVALID_ARGUMENT)
        .rejectionReason(BufferUtil.wrapString("bad name"))
        .tryWriteResponse(1, registration.requestId());

    // then the caller completes exceptionally with the rejection type + reason
    assertThat(registration.response()).isCompletedExceptionally();
    assertThatThrownBy(() -> registration.response().get())
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(CommandRejectionException.class)
        .hasMessage("bad name")
        .extracting(e -> ((CommandRejectionException) e).type())
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  private static BufferWriter bufferWriter(final byte[] payload) {
    return new BufferWriter() {
      @Override
      public int getLength() {
        return payload.length;
      }

      @Override
      public int write(final MutableDirectBuffer buffer, final int offset) {
        buffer.putBytes(offset, payload);
        return offset + payload.length;
      }
    };
  }
}
