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

  private static final String JSON_VALUE = "{\"key\":\"value\"}";
  private static final String JSON_VALUE_1 = "{\"key\":\"value1\"}";
  private static final String JSON_VALUE_2 = "{\"key\":\"value2\"}";
  private static final String JSON_NEW_VALUE = "{\"key\":\"newValue\"}";
  private static final String VARIABLE_NAME = "myVar";
  private static final String BPMN_PROCESS_ID = "process";
  private static final String TENANT_ID = "<default>";
  private static final long SCOPE_KEY = 1L;
  private static final long PROCESS_INSTANCE_KEY = 2L;
  private static final long PROCESS_DEFINITION_KEY = 3L;
  private static final long ROOT_PROCESS_INSTANCE_KEY = 4L;

  @Test
  void shouldCacheJsonValueOnRepeatedGetValueCalls() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(JSON_VALUE);
    record.setValue(new UnsafeBuffer(msgPack));

    // when
    final String firstCall = record.getValue();
    final String secondCall = record.getValue();
    final String thirdCall = record.getValue();

    // then - all calls should return the exact same String instance (not just equal strings)
    assertThat(firstCall).isEqualTo(JSON_VALUE);
    assertThat(secondCall).isSameAs(firstCall);
    assertThat(thirdCall).isSameAs(firstCall);
  }

  @Test
  void shouldInvalidateCacheWhenSetValueIsCalled() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack1 = MsgPackConverter.convertToMsgPack(JSON_VALUE_1);
    record.setValue(new UnsafeBuffer(msgPack1));

    final String firstValue = record.getValue();
    assertThat(firstValue).isEqualTo(JSON_VALUE_1);

    // when - setting a new value should invalidate the cache
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack(JSON_VALUE_2);
    record.setValue(new UnsafeBuffer(msgPack2));

    // then
    final String secondValue = record.getValue();
    assertThat(secondValue).isEqualTo(JSON_VALUE_2);
    assertThat(secondValue).isNotSameAs(firstValue);
  }

  @Test
  void shouldInvalidateCacheWhenSetValueWithOffsetIsCalled() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack1 = MsgPackConverter.convertToMsgPack(JSON_VALUE_1);
    record.setValue(new UnsafeBuffer(msgPack1));

    final String firstValue = record.getValue();
    assertThat(firstValue).isEqualTo(JSON_VALUE_1);

    // when - setting a new value with offset should invalidate the cache
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack(JSON_VALUE_2);
    final var buffer = new UnsafeBuffer(msgPack2);
    record.setValue(buffer, 0, buffer.capacity());

    // then
    final String secondValue = record.getValue();
    assertThat(secondValue).isEqualTo(JSON_VALUE_2);
    assertThat(secondValue).isNotSameAs(firstValue);
  }

  @Test
  void shouldInvalidateCacheWhenRecordIsReset() {
    // given
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(JSON_VALUE);
    record.setValue(new UnsafeBuffer(msgPack));

    final String cachedValue = record.getValue();
    assertThat(cachedValue).isEqualTo(JSON_VALUE);

    // when - reset the record and set a new value
    record.reset();
    final byte[] msgPack2 = MsgPackConverter.convertToMsgPack(JSON_NEW_VALUE);
    record.setValue(new UnsafeBuffer(msgPack2));

    // then - the cache should be invalidated and a fresh conversion should happen
    final String afterReset = record.getValue();
    assertThat(afterReset).isEqualTo(JSON_NEW_VALUE);
    assertThat(afterReset).isNotSameAs(cachedValue);
  }

  @Test
  void shouldInvalidateCacheWhenRecordIsWrapped() {
    // given
    final var record = createFullRecord(JSON_VALUE_1);

    final String cachedValue = record.getValue();
    assertThat(cachedValue).isEqualTo(JSON_VALUE_1);

    // when - serialize and re-wrap with different data (simulates how the engine reuses records)
    final var record2 = createFullRecord(JSON_VALUE_2);
    final var writeBuffer2 = new UnsafeBuffer(new byte[record2.getLength()]);
    record2.write(writeBuffer2, 0);

    // populate cache on record before wrapping
    record.getValue();

    // wrap with the new data
    record.wrap(writeBuffer2, 0, record2.getLength());

    // then - the value should reflect the new data, not the cached one
    final String afterWrap = record.getValue();
    assertThat(afterWrap).isEqualTo(JSON_VALUE_2);
    assertThat(afterWrap).isNotSameAs(cachedValue);
  }

  private VariableRecord createFullRecord(final String jsonValue) {
    final var record = new VariableRecord();
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(jsonValue);
    record.setValue(new UnsafeBuffer(msgPack));
    record.setName(new UnsafeBuffer(VARIABLE_NAME.getBytes()));
    record.setScopeKey(SCOPE_KEY);
    record.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    record.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    record.setBpmnProcessId(new UnsafeBuffer(BPMN_PROCESS_ID.getBytes()));
    record.setTenantId(TENANT_ID);
    record.setRootProcessInstanceKey(ROOT_PROCESS_INSTANCE_KEY);
    return record;
  }
}
