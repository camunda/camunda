/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceKind;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ProcessInstanceRecordTest {

  @Test
  void shouldDefaultKindToProcessInstance() {
    // given
    final var record = new ProcessInstanceRecord();

    // when
    final var kind = record.getKind();

    // then
    assertThat(kind).isEqualTo(ProcessInstanceKind.PROCESS_INSTANCE);
  }

  @Test
  void shouldDefaultKindToProcessInstanceWhenAbsent() {
    // given - a record written by a version that did not know the kind property
    final byte[] msgPack =
        MsgPackConverter.convertToMsgPack(
            "{\"bpmnProcessId\":\"process\",\"processInstanceKey\":1}");
    final var record = new ProcessInstanceRecord();

    // when
    record.wrap(new UnsafeBuffer(msgPack), 0, msgPack.length);

    // then
    assertThat(record.getKind()).isEqualTo(ProcessInstanceKind.PROCESS_INSTANCE);
  }

  @Test
  void shouldCopyKindOnWrap() {
    // given
    final var source = new ProcessInstanceRecord().setKind(ProcessInstanceKind.CASE_INSTANCE);

    // when
    final var copy = new ProcessInstanceRecord();
    copy.wrap(source);

    // then
    assertThat(copy.getKind()).isEqualTo(ProcessInstanceKind.CASE_INSTANCE);
  }

  @Test
  void shouldRoundTripKindThroughMsgPack() {
    // given
    final var source = new ProcessInstanceRecord().setKind(ProcessInstanceKind.CASE_INSTANCE);
    final var buffer = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(buffer, 0);

    // when
    final var read = new ProcessInstanceRecord();
    read.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(read.getKind()).isEqualTo(ProcessInstanceKind.CASE_INSTANCE);
  }
}
