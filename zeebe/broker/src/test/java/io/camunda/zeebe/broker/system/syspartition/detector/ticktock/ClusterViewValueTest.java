/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ClusterViewValueTest {

  @Test
  void shouldSerializeSameViewToSameBytes() {
    // given - the leader of each term may build the view in a different order
    final var oneOrder = new LinkedHashMap<String, Long>();
    oneOrder.put("2", 3L);
    oneOrder.put("0", 5L);
    final var otherOrder = new LinkedHashMap<String, Long>();
    otherOrder.put("0", 5L);
    otherOrder.put("2", 3L);

    // when
    final var oneBytes =
        BufferUtil.bufferAsArray(
            BufferUtil.createCopy(new ClusterViewValue().setLastSeenTicks(oneOrder)));
    final var otherBytes =
        BufferUtil.bufferAsArray(
            BufferUtil.createCopy(new ClusterViewValue().setLastSeenTicks(otherOrder)));

    // then
    assertThat(oneBytes).isEqualTo(otherBytes);
  }

  @Test
  void shouldReadBackLastSeenTicks() {
    // given
    final var written = new ClusterViewValue().setLastSeenTicks(Map.of("0", 5L, "1", 4L));
    final var read = new ClusterViewValue();

    // when
    read.wrap(BufferUtil.createCopy(written));

    // then
    assertThat(read.lastSeenTicks()).containsExactly(Map.entry("0", 5L), Map.entry("1", 4L));
  }

  @Test
  void shouldReadBackTickWithTocks() {
    // given
    final var written =
        new TickValue().setTickId(7).setLeaderId("1").setTocks(Map.of("0", 6L, "2", 5L));
    final var read = new TickValue();

    // when
    read.wrap(BufferUtil.createCopy(written));

    // then
    assertThat(read.getTickId()).isEqualTo(7);
    assertThat(read.getLeaderId()).isEqualTo("1");
    assertThat(read.getTocks()).containsExactly(Map.entry("0", 6L), Map.entry("2", 5L));
  }
}
