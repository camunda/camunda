/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link IncidentV1} is a frozen copy of the property based implementation the generated
 * codec replaced.
 */
final class IncidentSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_MESSAGE =
      "an error message that is longer than thirty one bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new IncidentV1();
    v1.setRecord(populatedRecord());
    final var current = new Incident();
    current.setRecord(populatedRecord());

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given
    final var v1 = new IncidentV1();
    final var current = new Incident();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new IncidentV1();
    v1.setRecord(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new Incident();
    current.setRecord(new IncidentRecord().setErrorMessage("dirty").setJobKey(9L));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRecord().getErrorMessage()).isEqualTo(LONG_MESSAGE);
    assertThat(current.getRecord().getJobKey()).isEqualTo(42L);
    assertThat(current.getRecord().getProcessInstanceKey()).isEqualTo(7L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new Incident();
    current.setRecord(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new IncidentV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRecord().getErrorMessage()).isEqualTo(LONG_MESSAGE);
    assertThat(v1.getRecord().getJobKey()).isEqualTo(42L);
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new Incident();
    instance.setRecord(populatedRecord());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new Incident();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getRecord().getErrorMessage()).isEqualTo(LONG_MESSAGE);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new Incident();
    source.setRecord(populatedRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // One entry: the map header is 0x80 | count, so announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new Incident();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRecord(new IncidentRecord().setJobKey(8L));
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static IncidentRecord populatedRecord() {
    return new IncidentRecord()
        .setProcessDefinitionKey(3L)
        .setJobKey(42L)
        .setProcessInstanceKey(7L)
        .setElementInstanceKey(8L)
        .setVariableScopeKey(9L)
        .setErrorType(ErrorType.JOB_NO_RETRIES)
        .setErrorMessage(LONG_MESSAGE)
        .setTenantId("tenant-a")
        .setStorageOrdinal(5);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
