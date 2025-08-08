/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.Map;
import java.util.Set;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class UsageMetricRecordTest {

  @Test
  void testCounterValues() {
    // given
    final UsageMetricRecord usageMetricRecord = new UsageMetricRecord();
    final Map<String, Long> counterValues = Map.of("tenant1", 63L, "tenant2", 91L);
    final byte[] msgPackBytes = MsgPackConverter.convertToMsgPack(counterValues);
    final MutableDirectBuffer buffer = new UnsafeBuffer(msgPackBytes);
    // when
    usageMetricRecord.setCounterValues(buffer);
    // then
    final Map<String, Long> actualCounterValues = usageMetricRecord.getCounterValues();
    assertThat(actualCounterValues).isEqualTo(counterValues);
  }

  @Test
  void testSetValues() {
    // given
    final UsageMetricRecord usageMetricRecord = new UsageMetricRecord();
    final Map<String, Set<Long>> setValues = Map.of("tenant1", Set.of(1234567L, 7654321L));
    final byte[] msgPackBytes = MsgPackConverter.convertToMsgPack(setValues);
    final MutableDirectBuffer buffer = new UnsafeBuffer(msgPackBytes);
    // when
    usageMetricRecord.setSetValues(buffer);
    // then
    final Map<String, Set<Long>> actualSetValues = usageMetricRecord.getSetValues();
    assertThat(actualSetValues).containsKey("tenant1");
    assertThat(actualSetValues.get("tenant1")).containsExactlyInAnyOrder(1234567L, 7654321L);
  }
}
