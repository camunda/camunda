/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformerSlot;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedProcessV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class PersistedProcessSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var record = populatedRecord();
    final var v1 = new PersistedProcessV1();
    v1.wrap(record, 42L);
    v1.setState(PersistedProcessV1.PersistedProcessState.DRAINING);
    v1.setDeleteHistory(true);
    final var current = new PersistedProcess();
    current.wrap(record, 42L);
    current.setState(PersistedProcessState.DRAINING);
    current.setDeleteHistory(true);

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
    // given a record without transformer versions, and the default state and history flag
    final var record = defaultRecord();
    final var v1 = new PersistedProcessV1();
    v1.wrap(record, 42L);
    final var current = new PersistedProcess();
    current.wrap(record, 42L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given
    final var v1 = new PersistedProcessV1();
    final var current = new PersistedProcess();

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedProcessV1();
    v1.wrap(populatedRecord(), 42L);
    v1.setState(PersistedProcessV1.PersistedProcessState.DRAINING);
    v1.setDeleteHistory(true);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedProcess();
    current.wrap(otherRecord(), 7L);
    current.setState(PersistedProcessState.PENDING_DELETION);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getKey()).isEqualTo(42L);
    assertThat(current.getVersion()).isEqualTo(2);
    assertThat(BufferUtil.bufferAsString(current.getBpmnProcessId())).isEqualTo("process");
    assertThat(BufferUtil.bufferAsString(current.getResource())).isEqualTo("<bpmn/>");
    assertThat(current.getState()).isEqualTo(PersistedProcessState.DRAINING);
    assertThat(current.isDeleteHistory()).isTrue();
    assertThat(current.getTransformerVersions())
        .isEqualTo(Map.of(TransformerSlot.ERROR, 2, TransformerSlot.PROCESS, 3));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new PersistedProcessV1();
    v1.wrap(defaultRecord(), 42L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedProcess();
    current.wrap(populatedRecord(), 7L);
    current.setState(PersistedProcessState.DRAINING);
    current.setDeleteHistory(true);

    // when
    current.wrap(bytes, 0, length);

    // then the defaults are back, including the empty transformer versions
    assertThat(current.getState()).isEqualTo(PersistedProcessState.ACTIVE);
    assertThat(current.isDeleteHistory()).isFalse();
    assertThat(current.getTransformerVersions()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedProcess();
    current.wrap(populatedRecord(), 42L);
    current.setState(PersistedProcessState.DRAINING);
    current.setDeleteHistory(true);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedProcessV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getKey()).isEqualTo(42L);
    assertThat(v1.getVersion()).isEqualTo(2);
    assertThat(v1.getState()).isEqualTo(PersistedProcessV1.PersistedProcessState.DRAINING);
    assertThat(v1.isDeleteHistory()).isTrue();
    assertThat(v1.getTransformerVersions())
        .isEqualTo(Map.of(TransformerSlot.ERROR, 2, TransformerSlot.PROCESS, 3));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedProcess();
    source.wrap(populatedRecord(), 42L);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has eleven entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedProcess();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeploymentKey(99L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static ProcessRecord populatedRecord() {
    return new ProcessRecord()
        .setBpmnProcessId("process")
        .setResourceName("process.bpmn")
        .setResource(BufferUtil.wrapString("<bpmn/>"))
        .setVersion(2)
        .setTenantId("tenant")
        .setDeploymentKey(5L)
        .setVersionTag("tag")
        .setTransformerVersions(Map.of(1, 2, 7, 3));
  }

  private static ProcessRecord otherRecord() {
    return new ProcessRecord()
        .setBpmnProcessId("other")
        .setResourceName("other.bpmn")
        .setResource(BufferUtil.wrapString("other"))
        .setVersion(1)
        .setTenantId("other")
        .setDeploymentKey(1L)
        .setVersionTag("other");
  }

  private static ProcessRecord defaultRecord() {
    return new ProcessRecord()
        .setBpmnProcessId("process")
        .setResourceName("process.bpmn")
        .setResource(BufferUtil.wrapString("<bpmn/>"))
        .setVersion(1)
        .setTenantId("tenant")
        .setDeploymentKey(5L)
        .setVersionTag("");
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
