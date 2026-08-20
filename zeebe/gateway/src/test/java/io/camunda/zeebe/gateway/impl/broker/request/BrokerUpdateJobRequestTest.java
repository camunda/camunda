/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.junit.jupiter.api.Test;

final class BrokerUpdateJobRequestTest {

  @Test
  void shouldIncludePriorityInChangedAttributesWhenSet() {
    // given/when
    final var request = new BrokerUpdateJobRequest(1L, null, null, 80);

    // then
    assertThat(request.getRequestWriter()).isInstanceOf(JobRecord.class);
    final var record = (JobRecord) request.getRequestWriter();
    assertThat(record.getChangedAttributes()).contains(JobRecord.PRIORITY);
    assertThat(record.getPriority()).isEqualTo(80);
  }

  @Test
  void shouldExcludePriorityFromChangedAttributesWhenNull() {
    // given/when
    final var request = new BrokerUpdateJobRequest(1L, 3, null, null);

    // then
    final var record = (JobRecord) request.getRequestWriter();
    assertThat(record.getChangedAttributes()).doesNotContain(JobRecord.PRIORITY);
  }

  @Test
  void shouldIncludePriorityZeroInChangedAttributes() {
    // given/when
    final var request = new BrokerUpdateJobRequest(1L, null, null, 0);

    // then
    final var record = (JobRecord) request.getRequestWriter();
    assertThat(record.getChangedAttributes()).contains(JobRecord.PRIORITY);
    assertThat(record.getPriority()).isEqualTo(0);
  }
}
