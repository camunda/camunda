/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.camunda.zeebe.protocol.record.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class CommandApiRequestReaderTest {

  private static final Map<String, Object> CLAIMS =
      Map.of("authorized_username", "user", "authorized_tenants", List.of("tenant-a", "tenant-b"));

  private final CommandApiRequestReader reader = new CommandApiRequestReader();

  @Test
  void shouldReadAuthorization() {
    // given
    final var buffer = write(request().setClaims(CLAIMS));

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    final var authorization = reader.metadata().getAuthorization();
    assertThat(authorization.getUsername()).isEqualTo("user");
    assertThat(authorization.getTenants()).containsExactly("tenant-a", "tenant-b");
  }

  @Test
  void shouldNotReflectLaterChangesToRequestBuffer() {
    // given
    final var buffer = write(request().setClaims(CLAIMS));
    reader.wrap(buffer, 0, buffer.capacity());

    // when - the buffer is reused for the next request
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    final var authorization = reader.metadata().getAuthorization();
    assertThat(authorization.getUsername()).isEqualTo("user");
    assertThat(authorization.getTenants()).containsExactly("tenant-a", "tenant-b");
  }

  @Test
  void shouldReadEmptyAuthorizationWhenRequestHasNone() {
    // given
    final var buffer = write(request());

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.metadata().getAuthorization()).isEqualTo(AuthInfo.EMPTY);
  }

  @Test
  void shouldReadEmptyAuthorizationWhenItsLengthIsZero() {
    // given
    final var buffer = encodeWithoutAuthorization(true);

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.metadata().getAuthorization()).isEqualTo(AuthInfo.EMPTY);
  }

  @Test
  void shouldReadEmptyAuthorizationWhenRequestEndsAfterValue() {
    // given - requests of older clients end after the value
    final var buffer = encodeWithoutAuthorization(false);

    // when
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.metadata().getAuthorization()).isEqualTo(AuthInfo.EMPTY);
  }

  @Test
  void shouldNotKeepAuthorizationOfPreviousRequestAfterReset() {
    // given
    final var buffer = write(request().setClaims(CLAIMS));
    reader.wrap(buffer, 0, buffer.capacity());

    // when
    reader.reset();

    // then
    assertThat(reader.metadata().getAuthorization()).isEqualTo(AuthInfo.EMPTY);
  }

  private static ExecuteCommandRequest request() {
    final var value = valueBytes();
    return new ExecuteCommandRequest()
        .setPartitionId(1)
        .setValueType(ValueType.PROCESS_INSTANCE_CREATION)
        .setIntent(ProcessInstanceCreationIntent.CREATE)
        .setValue(value, 0, value.capacity());
  }

  private static DirectBuffer valueBytes() {
    final var record = new ProcessInstanceCreationRecord().setBpmnProcessId("process");
    final var bytes = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(bytes, 0);
    return bytes;
  }

  private static UnsafeBuffer write(final ExecuteCommandRequest request) {
    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);
    return buffer;
  }

  /**
   * Encodes a request without a tool name, and with either a zero-length authorization or no
   * authorization field at all.
   */
  private static UnsafeBuffer encodeWithoutAuthorization(final boolean withEmptyAuthorization) {
    final var value = valueBytes();
    final var buffer =
        new UnsafeBuffer(
            new byte
                [MessageHeaderEncoder.ENCODED_LENGTH
                    + ExecuteCommandRequestEncoder.BLOCK_LENGTH
                    + ExecuteCommandRequestEncoder.valueHeaderLength()
                    + value.capacity()
                    + ExecuteCommandRequestEncoder.authorizationHeaderLength()
                    + ExecuteCommandRequestEncoder.toolNameHeaderLength()]);
    final var encoder =
        new ExecuteCommandRequestEncoder()
            .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
            .partitionId(1)
            .valueType(ValueType.PROCESS_INSTANCE_CREATION)
            .intent(ProcessInstanceCreationIntent.CREATE.value())
            .putValue(value, 0, value.capacity());
    if (!withEmptyAuthorization) {
      final int length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
      return new UnsafeBuffer(buffer, 0, length);
    }

    final var empty = new UnsafeBuffer(0, 0);
    encoder.putAuthorization(empty, 0, 0).putToolName(empty, 0, 0);
    return buffer;
  }
}
