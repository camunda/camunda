/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.configuration.system.Actor;
import io.camunda.configuration.system.ActorPool;
import io.camunda.configuration.system.LegacyQueryApi;
import io.camunda.configuration.system.Restore;
import io.camunda.configuration.system.Upgrade;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class System {
  private static final String PREFIX = "camunda.system";

  private static final Set<String> LEGACY_CPU_THREAD_COUNT_PROPERTIES =
      Set.of("zeebe.broker.threads.cpuThreadCount");
  private static final Set<String> LEGACY_IO_THREAD_COUNT_PROPERTIES =
      Set.of("zeebe.broker.threads.ioThreadCount");
  private static final Set<String> LEGACY_CLOCK_CONTROLLED_PROPERTIES =
      Set.of("zeebe.clock.controlled");

  /**
   * Controls the number of non-blocking CPU threads in the broker-wide actor pool. Unless
   * overridden, one CPU thread is used per partition configured by camunda.cluster.partition-count
   * (legacy zeebe.broker.cluster.partitionsCount). When {@code camunda.system.actor-pool.enabled}
   * is true, this still sizes the shared pool for actors that are not specific to a physical
   * tenant; each tenant's pool is additional. Keep the CPU thread count within the number of
   * available physical cores.
   */
  private Integer cpuThreadCount;

  /**
   * Controls the number of IO threads in the broker-wide actor pool. These threads are used for
   * workloads that write data to disk. While writing, these threads are blocked which means that
   * they yield the CPU. Unless overridden, one IO thread is used per partition configured by
   * camunda.cluster.partition-count (legacy zeebe.broker.cluster.partitionsCount). When {@code
   * camunda.system.actor-pool.enabled} is true, this still sizes the shared pool for actors that
   * are not specific to a physical tenant; each tenant's pool is additional.
   */
  private Integer ioThreadCount;

  /**
   * Controls whether the system clock or mutable one. When enabled, time progression can be
   * controlled programmatically for testing purposes.
   */
  private boolean clockControlled = false;

  @NestedConfigurationProperty private Actor actor = new Actor();
  @NestedConfigurationProperty private Upgrade upgrade = new Upgrade();
  @NestedConfigurationProperty private ActorPool actorPool = new ActorPool();
  @NestedConfigurationProperty private Restore restore = new Restore();
  @NestedConfigurationProperty private LegacyQueryApi legacyQueryApi = new LegacyQueryApi();

  public Integer getCpuThreadCount() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".cpu-thread-count",
        cpuThreadCount,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CPU_THREAD_COUNT_PROPERTIES);
  }

  public void setCpuThreadCount(final Integer cpuThreadCount) {
    this.cpuThreadCount = cpuThreadCount;
  }

  public Integer getIoThreadCount() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".io-thread-count",
        ioThreadCount,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_IO_THREAD_COUNT_PROPERTIES);
  }

  public void setIoThreadCount(final Integer ioThreadCount) {
    this.ioThreadCount = ioThreadCount;
  }

  public boolean getClockControlled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
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

  public ActorPool getActorPool() {
    return actorPool;
  }

  public void setActorPool(final ActorPool actorPool) {
    this.actorPool = actorPool;
  }

  public Upgrade getUpgrade() {
    return upgrade;
  }

  public void setUpgrade(final Upgrade upgrade) {
    this.upgrade = upgrade;
  }

  public Restore getRestore() {
    return restore;
  }

  public void setRestore(final Restore restore) {
    this.restore = restore;
  }

  public LegacyQueryApi getLegacyQueryApi() {
    return legacyQueryApi;
  }

  public void setLegacyQueryApi(final LegacyQueryApi legacyQueryApi) {
    this.legacyQueryApi = legacyQueryApi;
  }
}
