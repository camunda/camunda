/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Builds a test {@link CurrentClusterConfiguration} for a cluster with only the default physical
 * tenant, one broker at a time: every broker added is active, and replicates the default tenant's
 * partitions it is given. A broker given no partitions is not part of the tenant until it is.
 */
public record DefaultTenantConfiguration(CurrentClusterConfiguration configuration) {

  public DefaultTenantConfiguration {
    Objects.requireNonNull(configuration, "configuration must not be null");
  }

  /** An initialized configuration with no brokers and an empty default tenant. */
  public static DefaultTenantConfiguration init() {
    return new DefaultTenantConfiguration(
        CurrentClusterConfiguration.init().initPartitionGroup(DEFAULT_GROUP));
  }

  /** Whether the broker is part of the cluster, whether or not it replicates any partition. */
  public boolean hasMember(final MemberId member) {
    return configuration.globalConfiguration().hasMember(member);
  }

  public DefaultTenantConfiguration addMember(final MemberId member) {
    return addMember(member, Map.of());
  }

  public DefaultTenantConfiguration addMember(
      final MemberId member, final Map<Integer, PartitionState> partitions) {
    var updated =
        configuration.updateGlobalConfiguration(
            global -> global.addMember(member, BrokerState.initializeAsActive()));
    if (!partitions.isEmpty()) {
      updated =
          updated.updatePartitionGroupConfig(
              DEFAULT_GROUP,
              group -> group.addMember(member, BrokerPartitionState.initialize(partitions)));
    }
    return new DefaultTenantConfiguration(updated);
  }

  /**
   * Updates the broker's state in the default tenant, adding the broker to the tenant first if it
   * is not part of it yet.
   */
  public DefaultTenantConfiguration updateMember(
      final MemberId member, final UnaryOperator<BrokerPartitionState> updater) {
    return new DefaultTenantConfiguration(
        configuration.updatePartitionGroupConfig(
            DEFAULT_GROUP,
            group ->
                group.hasMember(member)
                    ? group.updateMember(member, updater)
                    : group.addMember(
                        member, updater.apply(BrokerPartitionState.initialize(Map.of())))));
  }

  public DefaultTenantConfiguration setPartitionDistributorConfig(
      final PartitionDistributorConfig partitionDistributorConfig) {
    return new DefaultTenantConfiguration(
        configuration.updateGlobalConfiguration(
            global -> global.setPartitionDistributorConfig(partitionDistributorConfig)));
  }
}
