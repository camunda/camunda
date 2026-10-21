/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import org.jspecify.annotations.Nullable;

/**
 * The cluster id in the Event Plane's object names. The publisher and the backup store that keeps
 * its log there both take it from here, so that they always name the same cluster.
 */
public final class EventPlaneClusterKey {

  private EventPlaneClusterKey() {}

  /**
   * The cluster id the Event Plane's configuration or the cluster configuration sets, or null if
   * the cluster has to use its generated id.
   */
  public static @Nullable String configured(final BrokerCfg brokerCfg) {
    final var eventPlaneId = brokerCfg.getExperimental().getEventPlane().getClusterId();
    return eventPlaneId != null ? eventPlaneId : brokerCfg.getCluster().getClusterId();
  }

  /**
   * The cluster id in the Event Plane's object names: {@link #configured(BrokerCfg)}, else the
   * cluster's generated id, else the cluster name.
   */
  public static String of(final PartitionTransitionContext context) {
    final var brokerCfg = context.getBrokerCfg();
    final var configured = configured(brokerCfg);
    return configured != null
        ? configured
        : context
            .getClusterConfigurationService()
            .getCurrentClusterConfiguration()
            .clusterId()
            .orElse(brokerCfg.getCluster().getClusterName());
  }
}
