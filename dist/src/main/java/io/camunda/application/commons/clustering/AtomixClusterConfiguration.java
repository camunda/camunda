/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.utils.Version;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class AtomixClusterConfiguration {

  private final ClusterConfig config;
<<<<<<< HEAD

  @Autowired
  public AtomixClusterConfiguration(final ClusterConfig config) {
    this.config = config;
=======
  private final String actorSchedulerName;
  private final MeterRegistry meterRegistry;

  @Autowired
  public AtomixClusterConfiguration(
      final ClusterConfig config,
      final SchedulerConfiguration schedulerConfiguration,
      final MeterRegistry meterRegistry) {

    this.config = config;
    actorSchedulerName =
        schedulerConfiguration != null
                && schedulerConfiguration.schedulerPrefix() != null
                && schedulerConfiguration.nodeId() != null
            ? String.format(
                "%s-%s", schedulerConfiguration.schedulerPrefix(), schedulerConfiguration.nodeId())
            : "";
    this.meterRegistry = meterRegistry;
>>>>>>> 4a4a7677 (feat: migrated MessagingMetrics to micrometer)
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster() {
<<<<<<< HEAD
    final var atomixCluster = new AtomixCluster(config, Version.from(VersionUtil.getVersion()));
=======
    final var atomixCluster =
        new AtomixCluster(
            config, Version.from(VersionUtil.getVersion()), actorSchedulerName, meterRegistry);
>>>>>>> 4a4a7677 (feat: migrated MessagingMetrics to micrometer)
    atomixCluster.start();
    return atomixCluster;
  }
}
