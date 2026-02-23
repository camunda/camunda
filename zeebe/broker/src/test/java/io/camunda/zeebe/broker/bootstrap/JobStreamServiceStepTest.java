/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class JobStreamServiceStepTest {

  @Nested
  final class ImmutableJobActivationPropertiesTest {
    @Test
    void shouldDeserializeImmutableActivationProperties() {
      // given
      final var worker = BufferUtil.wrapString("worker");
      final var properties =
          new JobActivationPropertiesImpl()
              .setTimeout(250)
              .setFetchVariables(List.of(new StringValue("foo"), new StringValue("bar")))
              .setWorker(worker, 0, worker.capacity())
              .setTenantIds(List.of("tenant1", "tenant2"))
              .setTenantFilter(TenantFilter.ASSIGNED);
      final var buffer = BufferUtil.createCopy(properties);

      // when
      final var immutable = JobStreamServiceStep.readJobActivationProperties(buffer);

      // then
      assertThat(immutable.worker()).isEqualTo(worker).isNotSameAs(worker);
      assertThat(immutable.timeout()).isEqualTo(250L);
      assertThat(immutable.fetchVariables())
          .containsExactlyInAnyOrder(BufferUtil.wrapString("foo"), BufferUtil.wrapString("bar"));
      assertThat(immutable.tenantIds()).containsExactlyInAnyOrder("tenant1", "tenant2");
      assertThat(immutable.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }
  }
}
