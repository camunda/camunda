/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link IndexedRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class IndexedRecordSerdeTest {

  private static final ProcessInstanceIntent STATE = ProcessInstanceIntent.ELEMENT_ACTIVATING;
  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_PROCESS_ID =
      "a bpmn process id that is longer than thirty one bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new IndexedRecordV1();
    v1.setKey(42L).setState(STATE).setValue(populatedRecord());
    final var current = new IndexedRecord();
    current.setKey(42L).setState(STATE).setValue(populatedRecord());

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
    // given only the state, which has no default and cannot be written unset
    final var v1 = new IndexedRecordV1();
    v1.setState(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    final var current = new IndexedRecord();
    current.setState(ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhenUnsetLikeV1() {
    // given
    final var v1 = new IndexedRecordV1();
    final var current = new IndexedRecord();

    // when
    final var v1Error = catchThrowable(() -> v1.write(new ExpandableArrayBuffer(), 0));
    final var currentError = catchThrowable(() -> current.write(new ExpandableArrayBuffer(), 0));

    // then
    assertThat(v1Error).isInstanceOf(MsgpackPropertyException.class);
    assertThat(currentError).isInstanceOf(MsgpackPropertyException.class);
    assertThat(currentError.getMessage()).isEqualTo(v1Error.getMessage());
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new IndexedRecordV1();
    v1.setKey(42L).setState(STATE).setValue(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new IndexedRecord();
    current
        .setKey(9L)
        .setState(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .setValue(new ProcessInstanceRecord().setBpmnProcessId("dirty").setVersion(9));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(current.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(current.getValue().getBpmnProcessId()).isEqualTo(LONG_PROCESS_ID);
    assertThat(current.getValue().getVersion()).isEqualTo(3);
    assertThat(current.getValue().getTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new IndexedRecord();
    current.setKey(42L).setState(STATE).setValue(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new IndexedRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(v1.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(v1.getValue().getBpmnProcessId()).isEqualTo(LONG_PROCESS_ID);
    assertThat(v1.getValue().getVersion()).isEqualTo(3);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new IndexedRecord();
    source.setKey(42L).setState(STATE).setValue(populatedRecord());
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
    final var instance = new IndexedRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(43L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static ProcessInstanceRecord populatedRecord() {
    return new ProcessInstanceRecord()
        .setBpmnProcessId(LONG_PROCESS_ID)
        .setVersion(3)
        .setProcessInstanceKey(7L)
        .setTenantId("tenant-a");
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
