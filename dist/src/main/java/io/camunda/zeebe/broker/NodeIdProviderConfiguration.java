/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.fromBrokerCopier;
import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.getNodeIdProvider;
import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.getS3NodeIdRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.BrokerDataDirectoryCopier;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.ConfiguredDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.NodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.VersionedNodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker"})
@DependsOn("unifiedConfigurationHelper")
@Import(BrokerShutdownHelper.class)
public class NodeIdProviderConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdProviderConfiguration.class);
  private final Cluster cluster;
  private final boolean disableVersionedDirectory;
  private final int versionedDirectoryRetentionCount;
  private final ObjectMapper objectMapper;

  @Autowired
  public NodeIdProviderConfiguration(
      final UnifiedConfiguration configuration, final ObjectMapper objectMapper) {
    cluster = configuration.getCamunda().getCluster();
    final var primaryStorage = configuration.getCamunda().getData().getPrimaryStorage();
    disableVersionedDirectory = primaryStorage.disableVersionedDirectory();
    versionedDirectoryRetentionCount = primaryStorage.getVersionedDirectoryRetentionCount();
    this.objectMapper = objectMapper;
  }

  @Bean
  /** Create the S3NodeReposiotry as a separate bean so it's lifecycle is managed by spring */
  public S3NodeIdRepository s3NodeIdRepository() {
    return getS3NodeIdRepository(cluster);
  }

  @Bean
  public NodeIdProvider nodeIdProvider(
      final Optional<NodeIdRepository> nodeIdRepository,
      final BrokerShutdownHelper shutdownHelper) {
    return getNodeIdProvider(
        LOG,
        cluster,
        nodeIdRepository,
        () ->
            shutdownHelper.initiateShutdown(
                1, "NodeIdProvider requested shutdown due to lease loss or failure"));
  }

  @Bean
  public DataDirectoryProvider dataDirectoryProvider(
      final NodeIdProvider nodeIdProvider,
      final NodeIdProviderReadinessAwaiter readinessAwaiter,
      final BrokerBasedConfiguration brokerBasedConfiguration) {

    if (!readinessAwaiter.isReady()) {
      throw new IllegalStateException("NodeIdProvider is not ready");
    }

    final var initializer =
        switch (cluster.getNodeIdProvider().getType()) {
          case FIXED -> new ConfiguredDataDirectoryProvider();
          case S3 -> {
            final var brokerCopier = new BrokerDataDirectoryCopier();
            final var nodeInstance = nodeIdProvider.currentNodeInstance();
            final var previousNodeGracefullyShutdown =
                nodeIdProvider.previousNodeGracefullyShutdown().join();
            yield disableVersionedDirectory
                ? new NodeIdBasedDataDirectoryProvider(nodeInstance)
                : new VersionedNodeIdBasedDataDirectoryProvider(
                    objectMapper,
                    nodeInstance,
                    fromBrokerCopier(brokerCopier),
                    previousNodeGracefullyShutdown,
                    versionedDirectoryRetentionCount);
          }
        };

    final DataCfg data = brokerBasedConfiguration.config().getData();
    final var directory = Path.of(data.getDirectory());
    final var configuredDirectory = initializer.initialize(directory).join();
    data.setDirectory(configuredDirectory.toString());

    return initializer;
  }

  @Bean
  public NodeIdProviderReadinessAwaiter nodeIdProviderReadinessAwaiter(
      final NodeIdProvider nodeIdProvider, final AtomixCluster atomixCluster) {
    final var membershipService = atomixCluster.getMembershipService();
    membershipService.addListener(
        l -> {
          switch (l.type()) {
            case MEMBER_ADDED, MEMBER_REMOVED ->
                nodeIdProvider.setMembers(membershipService.getMembers());
            default -> {
              // noop
            }
          }
        });

    // initialize current members
    nodeIdProvider.setMembers(membershipService.getMembers());

    final var isReady = nodeIdProvider.awaitReadiness().join();
    return new NodeIdProviderReadinessAwaiter(isReady);
  }

  public record NodeIdProviderReadinessAwaiter(boolean isReady) {}
}
