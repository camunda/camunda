/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;

/** Thread group for the non-blocking, CPU bound, tasks. */
public final class CpuThreadGroup extends ActorThreadGroup {

  public CpuThreadGroup(final ActorSchedulerBuilder builder) {
    super(
        "zb-actors",
        builder.isSimpleScheduling()
            ? simpleThreadCount(builder)
            : builder.getCpuBoundActorThreadCount(),
        builder,
        builder.getSchedulerName());
  }

  /**
   * SPIKE: sizing of the single group, as {threads, target}: at most {@code target} workers are
   * awake at once unless some are blocked. More runnable threads than the CPU quota only burn the
   * quota faster and get the whole pod throttled.
   *
   * <ul>
   *   <li>"auto" (default): cpu + io threads, target cpu; the io threads are spares that only run
   *       while other workers are blocked (blocking compensation).
   *   <li>"cpu": cpu threads, target cpu, no compensation.
   *   <li>"cpu+io": cpu + io threads, all awake (no limit).
   * </ul>
   */
  static int[] simpleSizing(final ActorSchedulerBuilder builder) {
    final int cpu = builder.getCpuBoundActorThreadCount();
    final int io = builder.getIoBoundActorThreadCount();
    return switch (System.getProperty("zeebe.actor.simpleThreads", "auto")) {
      case "auto" -> new int[] {cpu + io, cpu};
      case "cpu" -> new int[] {cpu, cpu};
      case "cpu+io" -> new int[] {cpu + io, cpu + io};
      default -> throw new IllegalArgumentException("zeebe.actor.simpleThreads");
    };
  }

  private static int simpleThreadCount(final ActorSchedulerBuilder builder) {
    return simpleSizing(builder)[0];
  }
}
