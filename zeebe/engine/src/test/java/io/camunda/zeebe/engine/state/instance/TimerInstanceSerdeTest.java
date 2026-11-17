/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.instance.legacy.TimerInstance} is a frozen copy of
 * the property based implementation the generated codec replaced.
 */
final class TimerInstanceSerdeTest {

  // longer than 31 bytes, so the msgpack string header changes
  private static final String LONG_HANDLER_ID =
      "a handler node id that is longer than thirty one bytes";
  private static final String LONG_PROCESS_ID =
      "a bpmn process id that is longer than thirty one bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.TimerInstance();
    populate(v1);
    final var current = new TimerInstance();
    populate(current);

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
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.TimerInstance();
    final var current = new TimerInstance();

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
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.TimerInstance();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new TimerInstance();
    dirty(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertPopulated(current);
  }

  @Test
  void shouldRestoreTheDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length =
        new io.camunda.zeebe.engine.state.instance.legacy.TimerInstance().write(bytes, 0);
    final var current = new TimerInstance();
    dirty(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new TimerInstance();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getRootProcessInstanceKey()).isEqualTo(TimerInstance.NO_ELEMENT_INSTANCE);
    assertThat(current.getKey()).isZero();
    assertThat(current.getRepetitions()).isZero();
    assertThat(current.getStorageOrdinal()).isZero();
    assertThat(current.getBpmnProcessId()).isEmpty();
    assertThat(current.getHandlerNodeId().capacity()).isZero();
    assertThat(current.getElementType()).isEqualTo(BpmnElementType.UNSPECIFIED);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new TimerInstance();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.engine.state.instance.legacy.TimerInstance();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(2L);
    assertThat(v1.getDueDate()).isEqualTo(5L);
    assertThat(v1.getRepetitions()).isEqualTo(300);
    assertThat(v1.getStorageOrdinal()).isEqualTo(70_000);
    assertThat(v1.getBpmnProcessId()).isEqualTo(LONG_PROCESS_ID);
    assertThat(v1.getHandlerNodeId()).isEqualTo(wrapString(LONG_HANDLER_ID));
    assertThat(v1.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new TimerInstance();
    populate(instance);
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new TimerInstance();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getBpmnProcessId()).isEqualTo(LONG_PROCESS_ID);
    assertThat(read.getHandlerNodeId()).isEqualTo(wrapString(LONG_HANDLER_ID));
    assertThat(read.getTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new TimerInstance();
    populate(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map header is 0x80 | count for up to 15 entries: announce one more entry.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new TimerInstance();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setKey(8L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static void populate(
      final io.camunda.zeebe.engine.state.instance.legacy.TimerInstance timer) {
    timer.setHandlerNodeId(wrapString(LONG_HANDLER_ID));
    timer.setProcessDefinitionKey(1L << 40);
    timer.setKey(2L);
    timer.setElementInstanceKey(3L);
    timer.setProcessInstanceKey(4L);
    timer.setDueDate(5L);
    timer.setRepetitions(300);
    timer.setTenantId("tenant-a");
    timer.setRootProcessInstanceKey(6L);
    timer.setStorageOrdinal(70_000);
    timer.setBpmnProcessId(LONG_PROCESS_ID);
    timer.setElementType(BpmnElementType.SERVICE_TASK);
  }

  private static void populate(final TimerInstance timer) {
    timer.setHandlerNodeId(wrapString(LONG_HANDLER_ID));
    timer.setProcessDefinitionKey(1L << 40);
    timer.setKey(2L);
    timer.setElementInstanceKey(3L);
    timer.setProcessInstanceKey(4L);
    timer.setDueDate(5L);
    timer.setRepetitions(300);
    timer.setTenantId("tenant-a");
    timer.setRootProcessInstanceKey(6L);
    timer.setStorageOrdinal(70_000);
    timer.setBpmnProcessId(LONG_PROCESS_ID);
    timer.setElementType(BpmnElementType.SERVICE_TASK);
  }

  private static void dirty(final TimerInstance timer) {
    timer.setHandlerNodeId(wrapString("dirty"));
    timer.setProcessDefinitionKey(9L);
    timer.setKey(9L);
    timer.setElementInstanceKey(9L);
    timer.setProcessInstanceKey(9L);
    timer.setDueDate(9L);
    timer.setRepetitions(9);
    timer.setTenantId("dirty");
    timer.setRootProcessInstanceKey(9L);
    timer.setStorageOrdinal(9);
    timer.setBpmnProcessId("dirty");
    timer.setElementType(BpmnElementType.START_EVENT);
  }

  private static void assertPopulated(final TimerInstance timer) {
    assertThat(timer.getHandlerNodeId()).isEqualTo(wrapString(LONG_HANDLER_ID));
    assertThat(timer.getProcessDefinitionKey()).isEqualTo(1L << 40);
    assertThat(timer.getKey()).isEqualTo(2L);
    assertThat(timer.getElementInstanceKey()).isEqualTo(3L);
    assertThat(timer.getProcessInstanceKey()).isEqualTo(4L);
    assertThat(timer.getDueDate()).isEqualTo(5L);
    assertThat(timer.getRepetitions()).isEqualTo(300);
    assertThat(timer.getTenantId()).isEqualTo("tenant-a");
    assertThat(timer.getRootProcessInstanceKey()).isEqualTo(6L);
    assertThat(timer.getStorageOrdinal()).isEqualTo(70_000);
    assertThat(timer.getBpmnProcessId()).isEqualTo(LONG_PROCESS_ID);
    assertThat(timer.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
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
