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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link
 * JobRecordValueV1} is a frozen copy of the hand-written implementation the generated codec
 * replaced.
 */
final class JobRecordValueSerdeTest {

  // longer than 31 bytes, so the msgpack string header is no longer the fixstr form
  private static final String LONG_TYPE = "a-job-type-name-that-is-longer-than-thirty-one-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new JobRecordValueV1();
    final var current = new JobRecordValue();
    final var record = jobRecord();
    v1.setRecordWithoutVariables(record);
    current.setRecordWithoutVariables(record);

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
    // given an empty record, everything stays default
    final var v1 = new JobRecordValueV1();
    final var current = new JobRecordValue();
    v1.setRecordWithoutVariables(new JobRecord());
    current.setRecordWithoutVariables(new JobRecord());

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
    final var v1 = new JobRecordValueV1();
    v1.setRecordWithoutVariables(jobRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobRecordValue();
    // dirty every value the stored record carries, plus one it does not (error fields)
    current.setRecordWithoutVariables(
        new JobRecord()
            .setType("stale")
            .setWorker("stale-worker")
            .setRetries(9)
            .setRetryBackoff(9L)
            .setRecurringTime(9L)
            .setDeadline(9L)
            .setTimeout(9L)
            .setProcessDefinitionVersion(9)
            .setProcessDefinitionKey(9L)
            .setProcessInstanceKey(9L)
            .setBpmnProcessId("stale-process")
            .setElementId("stale-element")
            .setErrorMessage("stale-error"));

    // when
    current.wrap(bytes, 0, length);

    // then
    final var record = current.getRecord();
    assertThat(record.getType()).isEqualTo(LONG_TYPE);
    assertThat(record.getWorker()).isEqualTo("worker-a");
    assertThat(record.getRetries()).isEqualTo(3);
    assertThat(record.getRetryBackoff()).isEqualTo(1_000L);
    assertThat(record.getRecurringTime()).isEqualTo(2_000L);
    assertThat(record.getDeadline()).isEqualTo(3_000L);
    assertThat(record.getTimeout()).isEqualTo(4_000L);
    assertThat(record.getProcessDefinitionVersion()).isEqualTo(7);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(70L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(700L);
    assertThat(record.getBpmnProcessId()).isEqualTo("process");
    assertThat(record.getElementId()).isEqualTo("service-task");
    assertThat(record.getErrorMessage()).isEmpty();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobRecordValue();
    current.setRecordWithoutVariables(jobRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new JobRecordValueV1();
    v1.wrap(bytes, 0, length);

    // then
    final var record = v1.getRecord();
    assertThat(record.getType()).isEqualTo(LONG_TYPE);
    assertThat(record.getWorker()).isEqualTo("worker-a");
    assertThat(record.getRetries()).isEqualTo(3);
    assertThat(record.getDeadline()).isEqualTo(3_000L);
    assertThat(record.getProcessDefinitionKey()).isEqualTo(70L);
    assertThat(record.getElementId()).isEqualTo("service-task");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new JobRecordValue();
    source.setRecordWithoutVariables(jobRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(BufferUtil.wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one declared property: fixmap header (0x80 | count), so announce one more entry at index 0
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobRecordValue();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.getRecord().setRetries(8);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    final var reread = new JobRecordValue();
    reread.wrap(rewritten, 0, rewrittenLength);
    assertThat(reread.getRecord().getRetries()).isEqualTo(8);
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new JobRecordValue();
    instance.setRecordWithoutVariables(jobRecord());
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new JobRecordValue();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getRecord().getType()).isEqualTo(LONG_TYPE);
    assertThat(read.getRecord().getWorker()).isEqualTo("worker-a");
    assertThat(read.getRecord().getElementId()).isEqualTo("service-task");
  }

  private static JobRecord jobRecord() {
    return new JobRecord()
        .setType(LONG_TYPE)
        .setWorker("worker-a")
        .setRetries(3)
        .setRetryBackoff(1_000L)
        .setRecurringTime(2_000L)
        .setDeadline(3_000L)
        .setTimeout(4_000L)
        .setProcessDefinitionVersion(7)
        .setProcessDefinitionKey(70L)
        .setProcessInstanceKey(700L)
        .setBpmnProcessId("process")
        .setElementId("service-task");
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
