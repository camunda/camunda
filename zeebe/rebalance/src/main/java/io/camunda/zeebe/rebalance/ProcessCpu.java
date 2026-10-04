/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import java.lang.management.ManagementFactory;
import java.util.function.LongSupplier;

/**
 * The CPU this broker's process uses.
 *
 * @param cpuTimeNanos the CPU time the process has used since it started, or a negative value if
 *     unknown; must be safe to call from any thread
 * @param cpus the CPUs the process may use, which in a container are those its quota allows
 */
public record ProcessCpu(LongSupplier cpuTimeNanos, int cpus) {

  public static ProcessCpu ofThisProcess() {
    final int cpus = Runtime.getRuntime().availableProcessors();
    if (ManagementFactory.getOperatingSystemMXBean()
        instanceof final com.sun.management.OperatingSystemMXBean os) {
      return new ProcessCpu(os::getProcessCpuTime, cpus);
    }
    return new ProcessCpu(() -> -1, cpus);
  }
}
