/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class PartitionUtilTest {

  @Test
  void shouldComputeHashCode() {
    assertThat(PartitionUtil.hashCode(wrapString("a"))).isEqualTo(97);
    assertThat(PartitionUtil.hashCode(wrapString("b"))).isEqualTo(98);
    assertThat(PartitionUtil.hashCode(wrapString("c"))).isEqualTo(99);
    assertThat(PartitionUtil.hashCode(wrapString("foobar"))).isEqualTo(-1268878963);
  }

  @Test
  void shouldGetZeroHashCodeIfEmpty() {
    assertThat(PartitionUtil.hashCode(new UnsafeBuffer())).isEqualTo(0);
  }

  @Test
  void shouldGetPartitionIdForCorrelationKey() {
    assertThat(PartitionUtil.getPartitionId(wrapString("a"), 10)).isEqualTo(7 + START_PARTITION_ID);
    assertThat(PartitionUtil.getPartitionId(wrapString("b"), 3)).isEqualTo(2 + START_PARTITION_ID);
    assertThat(PartitionUtil.getPartitionId(wrapString("c"), 11)).isEqualTo(0 + START_PARTITION_ID);
    assertThat(PartitionUtil.getPartitionId(wrapString("foobar"), 100))
        .isEqualTo(63 + START_PARTITION_ID);
  }
}
