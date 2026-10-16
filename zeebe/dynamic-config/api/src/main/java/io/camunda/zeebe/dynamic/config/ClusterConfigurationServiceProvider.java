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
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * Entry point through which the implementation module hands out its services, so that consumers
 * only compile against this API module and merely need the implementation on the runtime classpath.
 * Consumers should not use this directly but the {@code create} methods on the service interfaces.
 *
 * <p>The implementation is discovered via {@link ServiceLoader}.
 */
public interface ClusterConfigurationServiceProvider {

  ClusterConfigurationManagerService newManagerService(
      Path dataRootDirectory,
      ClusterCommunicationService communicationService,
      ClusterMembershipService membershipService,
      ClusterConfigurationGossiperConfig config,
      ClusterChangeExecutor clusterChangeExecutor,
      MeterRegistry meterRegistry);

  GatewayClusterConfigurationService newGatewayService(
      ClusterCommunicationService communicationService,
      ClusterMembershipService membershipService,
      ClusterConfigurationGossiperConfig config,
      MeterRegistry meterRegistry);

  ClusterConfigurationSerializer newProtobufSerializer();

  ClusterConfigurationRequestsSerializer newProtobufRequestsSerializer();

  /** Loads the provider on the classpath, failing if the implementation module is missing. */
  static ClusterConfigurationServiceProvider load() {
    return ServiceLoader.load(
            ClusterConfigurationServiceProvider.class,
            ClusterConfigurationServiceProvider.class.getClassLoader())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No ClusterConfigurationServiceProvider found; is zeebe-cluster-config-impl on the runtime classpath?"));
  }
}
