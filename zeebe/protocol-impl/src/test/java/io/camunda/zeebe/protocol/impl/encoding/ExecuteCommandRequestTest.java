/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ExecuteCommandRequestTest {

  private static final String TRACEPARENT =
      "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

  @Test
  void shouldRoundTripTraceContextWithExistingFields() {
    // given
    final var withTrace = newRequest().setToolName("my-tool").setTraceContext(TRACEPARENT);
    final var withoutTrace = newRequest();

    // when
    final var decodedWithTrace = roundTrip(withTrace);
    final var decodedWithoutTrace = roundTrip(withoutTrace);

    // then
    assertThat(decodedWithTrace.getTraceContext()).isEqualTo(TRACEPARENT);
    assertThat(decodedWithTrace.getToolName()).isEqualTo("my-tool");
    assertThat(decodedWithTrace.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);
    assertThat(decodedWithoutTrace.getTraceContext()).isEmpty();
  }

  @Test
  void shouldDecodeMessageFromOldSenderWithoutTraceContext() {
    // given a message as sent by a gateway still on schema version 11
    final var request = newRequest().setToolName("my-tool");
    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);
    final int oldLength =
        request.getLength() - ExecuteCommandRequestDecoder.traceContextHeaderLength();
    new MessageHeaderEncoder().wrap(buffer, 0).version(11);

    // when
    final var decoded = new ExecuteCommandRequest();
    decoded.wrap(buffer, 0, oldLength);

    // then
    assertThat(decoded.getTraceContext()).isEmpty();
    assertThat(decoded.getToolName()).isEqualTo("my-tool");
    assertThat(decoded.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);
  }

  @Test
  void shouldAllowOldReceiverToIgnoreTraceContext() {
    // given a v12 message with traceContext read by a v11 decoder
    final var request = newRequest().setToolName("my-tool").setTraceContext(TRACEPARENT);
    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);

    // when
    final var header = new MessageHeaderDecoder().wrap(buffer, 0);
    final var oldDecoder = new ExecuteCommandRequestDecoder();
    oldDecoder.wrap(buffer, header.encodedLength(), header.blockLength(), 11);
    oldDecoder.skipValue();
    oldDecoder.skipAuthorization();

    // then
    assertThat(oldDecoder.toolName()).isEqualTo("my-tool");
    assertThat(oldDecoder.limit()).isLessThan(buffer.capacity());
  }

  private static ExecuteCommandRequest roundTrip(final ExecuteCommandRequest request) {
    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);
    final var decoded = new ExecuteCommandRequest();
    decoded.wrap(buffer, 0, buffer.capacity());
    return decoded;
  }

  private static ExecuteCommandRequest newRequest() {
    return new ExecuteCommandRequest()
        .setPartitionId(1)
        .setValueType(ValueType.PROCESS_INSTANCE_CREATION)
        .setIntent(ProcessInstanceCreationIntent.CREATE)
        .setClaims(Map.of("sub", "user"))
        .setValue(new UnsafeBuffer(new byte[] {1, 2, 3}), 0, 3);
  }
}
