/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.function.BiConsumer;

/**
 * Measures the CPU time that the current thread spends on work of a physical tenant, and passes it
 * to a sink that charges the CPU budget of the tenant. Charging never delays or rejects the work.
 *
 * <p>Scopes do not count twice. A scope that opens on a thread inside another open scope measures
 * nothing, because the outer scope already covers its CPU time. This happens when a rejected task
 * runs on the submitting thread.
 */
public final class PhysicalTenantCpuCharger {

  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
  private static final ThreadLocal<Boolean> SCOPE_OPEN = new ThreadLocal<>();
  private static final Scope NO_SCOPE = physicalTenantId -> {};
  private static final PhysicalTenantCpuCharger NONE = new PhysicalTenantCpuCharger(null);

  private final BiConsumer<String, Long> sink;

  /**
   * @param sink receives the physical tenant id and the CPU time in nanoseconds
   */
  public PhysicalTenantCpuCharger(final BiConsumer<String, Long> sink) {
    this.sink = sink;
  }

  /** Returns a charger that measures nothing. */
  public static PhysicalTenantCpuCharger none() {
    return NONE;
  }

  /** Starts to measure the CPU time of the current thread. Call {@link Scope#chargeTo} to end. */
  public Scope open() {
    if (sink == null || SCOPE_OPEN.get() != null) {
      return NO_SCOPE;
    }
    SCOPE_OPEN.set(Boolean.TRUE);
    final long startNanos = THREADS.getCurrentThreadCpuTime();
    return physicalTenantId -> {
      SCOPE_OPEN.remove();
      sink.accept(physicalTenantId, THREADS.getCurrentThreadCpuTime() - startNanos);
    };
  }

  @FunctionalInterface
  public interface Scope {

    /** Ends the scope and charges its CPU time to the physical tenant. */
    void chargeTo(String physicalTenantId);
  }
}
