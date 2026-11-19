/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableSourceRecordTest {

  @Test
  void shouldHaveNoCausingElementByDefault() {
    // when
    final var source = new VariableSourceRecord();

    // then
    assertThat(source.getType()).isEqualTo(VariableOperationType.UNKNOWN);
    assertThat(source.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(source.getElementId()).isEmpty();
  }

  @Test
  void shouldCopyAllFieldsOnWrap() {
    // given
    final var original =
        new VariableSourceRecord()
            .setType(VariableOperationType.JOB_COMPLETION)
            .setElementInstanceKey(42L)
            .setElementId("ChargeCard");
    final var copy = new VariableSourceRecord();

    // when
    copy.wrap(original);

    // then
    assertThat(copy.getType()).isEqualTo(VariableOperationType.JOB_COMPLETION);
    assertThat(copy.getElementInstanceKey()).isEqualTo(42L);
    assertThat(copy.getElementId()).isEqualTo("ChargeCard");
  }

  @Test
  void shouldKeepCopiedElementIdWhenTheOriginalElementIdIsReplaced() {
    // given
    final var original = new VariableSourceRecord().setElementId("ChargeCard");
    final var copy = new VariableSourceRecord();
    copy.wrap(original);

    // when
    original.setElementId("ShipOrder");

    // then
    assertThat(copy.getElementId()).isEqualTo("ChargeCard");
  }

  @Test
  void shouldSetElementIdFromBuffer() {
    // given
    final var source = new VariableSourceRecord();

    // when
    source.setElementId(wrapString("ChargeCard"));

    // then
    assertThat(source.getElementId()).isEqualTo("ChargeCard");
  }

  @Test
  void shouldReadSourceWrittenBeforeTheCausingElementExisted() {
    // given a source as 8.10 writes it, with only the type property
    final var oldSource = MsgPackConverter.convertToMsgPack("{\"type\":\"API\"}");
    final var source = new VariableSourceRecord();

    // when
    source.wrap(new UnsafeBuffer(oldSource), 0, oldSource.length);

    // then
    assertThat(source.getType()).isEqualTo(VariableOperationType.API);
    assertThat(source.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(source.getElementId()).isEmpty();
  }

  @Test
  void shouldRoundTripThroughMsgPack() {
    // given
    final var original =
        new VariableSourceRecord()
            .setType(VariableOperationType.OUTPUT_MAPPING)
            .setElementInstanceKey(7L)
            .setElementId("ChargeCard");
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var readBack = new VariableSourceRecord();

    // when
    readBack.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(readBack.getType()).isEqualTo(VariableOperationType.OUTPUT_MAPPING);
    assertThat(readBack.getElementInstanceKey()).isEqualTo(7L);
    assertThat(readBack.getElementId()).isEqualTo("ChargeCard");
  }
}
