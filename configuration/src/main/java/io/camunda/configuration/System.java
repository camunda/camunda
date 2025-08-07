/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class System {
  private static final String PREFIX = "camunda.system";

  private static final Set<String> LEGACY_CPU_THREAD_COUNT_PROPERTIES =
      Set.of("zeebe.broker.threads.cpuThreadCount");
  private static final Set<String> LEGACY_IO_THREAD_COUNT_PROPERTIES =
      Set.of("zeebe.broker.threads.ioThreadCount");
  private static final Set<String> LEGACY_CLOCK_CONTROLLED_PROPERTIES =
      Set.of("zeebe.clock.controlled");

  /**
   * Controls the number of non-blocking CPU threads to be used. WARNING: You should never specify a
   * value that is larger than the number of physical cores available. Good practice is to leave 1-2
   * cores for IO threads and the operating system (it has to run somewhere). For example, when
   * running Zeebe on a machine which has 4 cores, a good value would be 2.
   */
  private int cpuThreadCount = 2;

  /**
   * Controls the number of io threads to be used. These threads are used for workloads that write
   * data to disk. While writing, these threads are blocked which means that they yield the CPU.
   */
  private int ioThreadCount = 2;

  /**
   * Controls whether the system clock or mutable one. When enabled, time progression can be
   * controlled programmatically for testing purposes.
   */
  private boolean clockControlled = false;

  private Actor actor = new Actor();
  private Upgrade upgrade = new Upgrade();

  public int getCpuThreadCount() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".cpu-thread-count",
        cpuThreadCount,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CPU_THREAD_COUNT_PROPERTIES);
  }

  public void setCpuThreadCount(final int cpuThreadCount) {
    this.cpuThreadCount = cpuThreadCount;
  }

  public int getIoThreadCount() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".io-thread-count",
        ioThreadCount,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_IO_THREAD_COUNT_PROPERTIES);
  }

  public void setIoThreadCount(final int ioThreadCount) {
    this.ioThreadCount = ioThreadCount;
  }

  public boolean getClockControlled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".clock-controlled",
        clockControlled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CLOCK_CONTROLLED_PROPERTIES);
  }

  public void setClockControlled(final boolean clockControlled) {
    this.clockControlled = clockControlled;
  }

  public Actor getActor() {
    return actor;
  }

  public void setActor(final Actor actor) {
    this.actor = actor;
  }

  public Upgrade getUpgrade() {
    return upgrade;
  }

  public void setUpgrade(final Upgrade upgrade) {
    this.upgrade = upgrade;
  }
}
