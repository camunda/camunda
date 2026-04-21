/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class JobRecordTest {

  @Test
  void shouldDefaultPriorityToZero() {
    // given/when
    final JobRecord record = new JobRecord();

    // then
    assertThat(record.getPriority()).isEqualTo(0L);
  }

  @Test
  void shouldSetAndGetPriority() {
    // given
    final JobRecord record = new JobRecord();

    // when
    record.setPriority(90L);

    // then
    assertThat(record.getPriority()).isEqualTo(90L);
  }

  @Test
  void shouldSupportNegativePriority() {
    // given
    final JobRecord record = new JobRecord();

    // when
    record.setPriority(-10L);

    // then
    assertThat(record.getPriority()).isEqualTo(-10L);
  }

  @Test
  void shouldSupportFullLongRangePriority() {
    // given
    final JobRecord record = new JobRecord();

    // when
    record.setPriority(Long.MAX_VALUE);

    // then
    assertThat(record.getPriority()).isEqualTo(Long.MAX_VALUE);
  }
}
