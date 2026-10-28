/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall.Listener;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PhysicalTenantCpuChargingInterceptorTest {

  private static final long BURN_NANOS = Duration.ofMillis(50).toNanos();

  private final List<String> chargedTenants = new ArrayList<>();
  private final List<Long> chargedNanos = new ArrayList<>();
  private final PhysicalTenantCpuChargingInterceptor interceptor =
      new PhysicalTenantCpuChargingInterceptor(
          (tenant, nanos) -> {
            chargedTenants.add(tenant);
            chargedNanos.add(nanos);
          });

  @Test
  void shouldChargeCpuTimeOfCallbacksToTenantOfCall() throws Exception {
    // given - a call of tenant "blue" whose handler burns CPU when the request is complete
    final var listener =
        Context.current()
            .withValue(InterceptorUtil.getPhysicalTenantIdKey(), "blue")
            .call(
                () ->
                    interceptor.interceptCall(
                        new NoopServerCall<>() {},
                        new Metadata(),
                        (call, headers) ->
                            new Listener<>() {
                              @Override
                              public void onHalfClose() {
                                burn();
                              }
                            }));

    // when - the callback runs outside of the context, like on an executor thread
    listener.onHalfClose();

    // then
    assertThat(chargedTenants).containsOnly("blue");
    assertThat(chargedNanos.stream().mapToLong(Long::longValue).sum()).isGreaterThan(BURN_NANOS);
  }

  @Test
  void shouldNotChargeCallWithoutTenant() {
    // when
    final var listener =
        interceptor.interceptCall(
            new NoopServerCall<>() {},
            new Metadata(),
            (call, headers) ->
                new Listener<>() {
                  @Override
                  public void onHalfClose() {
                    burn();
                  }
                });
    listener.onHalfClose();

    // then
    assertThat(chargedTenants).isEmpty();
  }

  private static void burn() {
    final var bean = ManagementFactory.getThreadMXBean();
    final long start = bean.getCurrentThreadCpuTime();
    while (bean.getCurrentThreadCpuTime() - start < BURN_NANOS + 1_000_000) {
      Thread.onSpinWait();
    }
  }
}
