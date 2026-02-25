/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableRecordTest {

  @Test
  void shouldCacheJsonValueOnRepeatedGetValueCalls() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack("{\"key\":\"value\"}");
    record.setValue(new UnsafeBuffer(msgPack));

    // when
    final String firstCall = record.getValue();
    final String secondCall = record.getValue();
    final String thirdCall = record.getValue();

    // then - all calls should return the exact same String instance (not just equal strings)
    assertThat(firstCall).isEqualTo("{\"key\":\"value\"}");
    assertThat(secondCall).isSameAs(firstCall);
    assertThat(thirdCall).isSameAs(firstCall);
  }

  @Test
  void shouldInvalidateCacheWhenSetValueIsCalled() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack1 = MsgPackConverter.convertToMsgPack("{\"key\":\"value1\"}");
    record.setValue(new UnsafeBuffer(msgPack1));

    final String firstValue = record.getValue();
    assertThat(firstValue).isEqualTo("{\"key\":\"value1\"}");

    // when - setting a new value should invalidate the cache
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack("{\"key\":\"value2\"}");
    record.setValue(new UnsafeBuffer(msgPack2));

    // then
    final String secondValue = record.getValue();
    assertThat(secondValue).isEqualTo("{\"key\":\"value2\"}");
    assertThat(secondValue).isNotSameAs(firstValue);
  }

  @Test
  void shouldInvalidateCacheWhenSetValueWithOffsetIsCalled() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack1 = MsgPackConverter.convertToMsgPack("{\"key\":\"value1\"}");
    record.setValue(new UnsafeBuffer(msgPack1));

    final String firstValue = record.getValue();
    assertThat(firstValue).isEqualTo("{\"key\":\"value1\"}");

    // when - setting a new value with offset should invalidate the cache
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack("{\"key\":\"value2\"}");
    final var buffer = new UnsafeBuffer(msgPack2);
    record.setValue(buffer, 0, buffer.capacity());

    // then
    final String secondValue = record.getValue();
    assertThat(secondValue).isEqualTo("{\"key\":\"value2\"}");
    assertThat(secondValue).isNotSameAs(firstValue);
  }

  @Test
  void shouldInvalidateCacheWhenRecordIsReset() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack("{\"key\":\"value\"}");
    record.setValue(new UnsafeBuffer(msgPack));

    final String cachedValue = record.getValue();
    assertThat(cachedValue).isEqualTo("{\"key\":\"value\"}");

    // when - reset the record and set a new value
    record.reset();
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack("{\"key\":\"newValue\"}");
    record.setValue(new UnsafeBuffer(msgPack2));

    // then - the cache should be invalidated and a fresh conversion should happen
    final String afterReset = record.getValue();
    assertThat(afterReset).isEqualTo("{\"key\":\"newValue\"}");
    assertThat(afterReset).isNotSameAs(cachedValue);
  }

  @Test
  void shouldInvalidateCacheWhenRecordIsWrapped() {
    // given
    final var record = createFullRecord("{\"key\":\"value1\"}");

    final String cachedValue = record.getValue();
    assertThat(cachedValue).isEqualTo("{\"key\":\"value1\"}");

    // when - serialize and re-wrap with different data (simulates how the engine reuses records)
    final var record2 = createFullRecord("{\"key\":\"value2\"}");
    final var writeBuffer2 = new UnsafeBuffer(new byte[record2.getLength()]);
    record2.write(writeBuffer2, 0);

    // populate cache on record before wrapping
    record.getValue();

    // wrap with the new data
    record.wrap(writeBuffer2, 0, record2.getLength());

    // then - the value should reflect the new data, not the cached one
    final String afterWrap = record.getValue();
    assertThat(afterWrap).isEqualTo("{\"key\":\"value2\"}");
    assertThat(afterWrap).isNotSameAs(cachedValue);
  }

  private VariableRecord createFullRecord(final String jsonValue) {
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(jsonValue);
    record.setValue(new UnsafeBuffer(msgPack));
    record.setName(new UnsafeBuffer("myVar".getBytes()));
    record.setScopeKey(1L);
    record.setProcessInstanceKey(2L);
    record.setProcessDefinitionKey(3L);
    record.setBpmnProcessId(new UnsafeBuffer("process".getBytes()));
    record.setTenantId("<default>");
    record.setRootProcessInstanceKey(4L);
    return record;
  }
}
