/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationRequestsSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;

/** Registered in {@code META-INF/services}; see {@link ClusterConfigurationServiceProvider}. */
public final class DefaultClusterConfigurationServiceProvider
    implements ClusterConfigurationServiceProvider {

  @Override
  public ClusterConfigurationManagerService newManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterConfigurationGossiperConfig config,
      final ClusterChangeExecutor clusterChangeExecutor,
      final MeterRegistry meterRegistry) {
    return new ClusterConfigurationManagerServiceImpl(
        dataRootDirectory,
        communicationService,
        membershipService,
        config,
        clusterChangeExecutor,
        meterRegistry);
  }

  @Override
  public GatewayClusterConfigurationService newGatewayService(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final ClusterConfigurationGossiperConfig config,
      final MeterRegistry meterRegistry) {
    return new GatewayClusterConfigurationServiceImpl(
        communicationService, membershipService, config, meterRegistry);
  }

  @Override
  public ClusterConfigurationSerializer newProtobufSerializer() {
    return new ProtoBufSerializer();
  }

  @Override
  public ClusterConfigurationRequestsSerializer newProtobufRequestsSerializer() {
    return new ProtoBufSerializer();
  }
}
