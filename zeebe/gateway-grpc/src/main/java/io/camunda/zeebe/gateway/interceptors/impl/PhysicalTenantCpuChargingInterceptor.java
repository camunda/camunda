/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.function.BiConsumer;

/**
 * Charges the CPU time of the threads that run a call to the physical tenant of the call. It never
 * delays or rejects a call.
 *
 * <p>The interceptor must run after the {@link AuthenticationInterceptor}, which puts the physical
 * tenant into the context of the call. A call without a physical tenant, such as a rejected call,
 * is not charged.
 *
 * <p>Only the work in the listener of the call is charged: the handler of the request, and the
 * start of the call. The work that completes the call on other threads, such as the answer from the
 * broker, is not charged.
 */
public final class PhysicalTenantCpuChargingInterceptor implements ServerInterceptor {

  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

  private final BiConsumer<String, Long> cpuCharger;

  /**
   * @param cpuCharger receives the physical tenant id and the CPU time in nanoseconds
   */
  public PhysicalTenantCpuChargingInterceptor(final BiConsumer<String, Long> cpuCharger) {
    this.cpuCharger = cpuCharger;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var physicalTenantId = InterceptorUtil.getPhysicalTenantIdKey().get();
    if (physicalTenantId == null) {
      return next.startCall(call, headers);
    }

    final long startNanos = THREADS.getCurrentThreadCpuTime();
    final Listener<ReqT> listener;
    try {
      listener = next.startCall(call, headers);
    } finally {
      charge(physicalTenantId, startNanos);
    }
    return new ChargingListener<>(listener, physicalTenantId);
  }

  private void charge(final String physicalTenantId, final long startNanos) {
    cpuCharger.accept(physicalTenantId, THREADS.getCurrentThreadCpuTime() - startNanos);
  }

  private final class ChargingListener<ReqT> extends SimpleForwardingServerCallListener<ReqT> {

    private final String physicalTenantId;

    private ChargingListener(final Listener<ReqT> delegate, final String physicalTenantId) {
      super(delegate);
      this.physicalTenantId = physicalTenantId;
    }

    @Override
    public void onMessage(final ReqT message) {
      charged(() -> super.onMessage(message));
    }

    @Override
    public void onHalfClose() {
      charged(super::onHalfClose);
    }

    @Override
    public void onCancel() {
      charged(super::onCancel);
    }

    @Override
    public void onComplete() {
      charged(super::onComplete);
    }

    @Override
    public void onReady() {
      charged(super::onReady);
    }

    private void charged(final Runnable callback) {
      final long startNanos = THREADS.getCurrentThreadCpuTime();
      try {
        callback.run();
      } finally {
        charge(physicalTenantId, startNanos);
      }
    }
  }
}
