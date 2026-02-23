/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.value.TenantFilter;
import org.junit.jupiter.api.Test;

/**
 * Tests for the TenantFilter property on JobBatchRecord to verify getter/setter functionality and
 * default value behavior.
 */
final class JobBatchRecordTenantFilterTest {

  @Test
  void shouldHaveDefaultTenantFilterOfProvided() {
    // given
    final var record = new JobBatchRecord();

    // when
    final var tenantFilter = record.getTenantFilter();

    // then
    assertThat(tenantFilter).isEqualTo(TenantFilter.PROVIDED);
  }

  @Test
  void shouldSetTenantFilterToAssigned() {
    // given
    final var record = new JobBatchRecord();

    // when
    record.setTenantFilter(TenantFilter.ASSIGNED);

    // then
    assertThat(record.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  @Test
  void shouldSetTenantFilterToProvided() {
    // given
    final var record = new JobBatchRecord();

    // when
    record.setTenantFilter(TenantFilter.PROVIDED);

    // then
    assertThat(record.getTenantFilter()).isEqualTo(TenantFilter.PROVIDED);
  }

  @Test
  void shouldMaintainTenantFilterAfterOtherPropertiesSet() {
    // given
    final var record = new JobBatchRecord();
    record.setTenantFilter(TenantFilter.ASSIGNED);

    // when - set other properties
    record.setType("test-type");
    record.setWorker("test-worker");
    record.setTimeout(1000L);
    record.setMaxJobsToActivate(5);

    // then - tenant filter should remain unchanged
    assertThat(record.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  @Test
  void shouldSerializeAndDeserializeTenantFilter() {
    // given
    final var original = new JobBatchRecord();
    original.setType("test-type");
    original.setWorker("test-worker");
    original.setTimeout(1000L);
    original.setMaxJobsToActivate(5);
    original.setTenantFilter(TenantFilter.ASSIGNED);

    // when - serialize to buffer
    final var buffer = new org.agrona.concurrent.UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);

    // and - deserialize from buffer
    final var deserialized = new JobBatchRecord();
    deserialized.wrap(buffer, 0, original.getLength());

    // then
    assertThat(deserialized.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    assertThat(deserialized.getType()).isEqualTo("test-type");
    assertThat(deserialized.getWorker()).isEqualTo("test-worker");
    assertThat(deserialized.getTimeout()).isEqualTo(1000L);
    assertThat(deserialized.getMaxJobsToActivate()).isEqualTo(5);
  }
}
