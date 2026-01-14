/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.BrokerDataDirectoryCopier;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RepositoryNodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.ConfiguredDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryValidator;
import io.camunda.zeebe.dynamic.nodeid.fs.NodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.VersionedNodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker", "restore"})
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
    return switch (cluster.getNodeIdProvider().getType()) {
      case FIXED -> null;
      case S3 -> {
        final var clientConfig = makeS3ClientConfig(cluster.getNodeIdProvider().s3());
        final var s3Config = cluster.getNodeIdProvider().s3();
        final var config =
            new S3NodeIdRepository.Config(
                s3Config.getBucketName(),
                s3Config.getLeaseDuration(),
                s3Config.getReadinessCheckTimeout());
        yield S3NodeIdRepository.of(clientConfig, config, Clock.systemUTC());
      }
    };
  }

  @Bean
  public NodeIdProvider nodeIdProvider(
      final Optional<NodeIdRepository> nodeIdRepository,
      final BrokerShutdownHelper shutdownHelper) {
    final var nodeIdProvider =
        switch (cluster.getNodeIdProvider().getType()) {
          case FIXED -> {
            final var nodeId = cluster.getNodeId();
            yield NodeIdProvider.staticProvider(nodeId);
          }
          case S3 -> {
            final var config = cluster.getNodeIdProvider().s3();
            if (nodeIdRepository.isEmpty()) {
              throw new IllegalStateException(
                  "DynamicNodeIdProvider configured to use S3: missing s3 node id repository");
            }
            final var taskId = config.getTaskId().orElse(UUID.randomUUID().toString());
            LOG.debug("Node configured with taskId {}", taskId);
            yield new RepositoryNodeIdProvider(
                nodeIdRepository.get(),
                Clock.systemUTC(),
                config.getLeaseDuration(),
                config.getLeaseAcquireMaxDelay(),
                config.getReadinessCheckTimeout(),
                taskId,
                () -> {
                  LOG.warn("NodeIdProvider terminating the process");
                  shutdownHelper.initiateShutdown(1);
                });
          }
        };
    nodeIdProvider.initialize(cluster.getSize()).join();

    return nodeIdProvider;
  }

  private static S3ClientConfig makeS3ClientConfig(final S3 s3) {
    final var credentials =
        s3.getAccessKey()
            .flatMap(
                accessKey ->
                    s3.getSecretKey()
                        .map(secretKey -> new S3ClientConfig.Credentials(accessKey, secretKey)));
    return new S3ClientConfig(
        credentials,
        s3.getRegion().map(Region::of),
        s3.getEndpoint().map(URI::create),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.ofNullable(s3.getApiCallTimeout()));
  }

  @Bean
  public DataDirectoryProvider dataDirectoryProvider(
      final NodeIdProvider nodeIdProvider,
      final NodeIdProviderReadinessAwaiter readinessAwaiter,
      final BrokerBasedConfiguration brokerBasedConfiguration,
      @Autowired(required = false) final DataDirectoryValidator dataDirectoryValidator) {

    if (!readinessAwaiter.isReady()) {
      throw new IllegalStateException("NodeIdProvider is not ready");
    }

    final DataDirectoryProvider initializer =
        switch (cluster.getNodeIdProvider().getType()) {
          case FIXED -> new ConfiguredDataDirectoryProvider();
          case S3 -> {
            final var brokerCopier = new BrokerDataDirectoryCopier();
            final var nodeInstance = nodeIdProvider.currentNodeInstance();
            final var previousNodeGracefullyShutdown =
                nodeIdProvider.previousNodeGracefullyShutdown().join();
            // Use custom validator if provided (for testing), otherwise use default from
            // copier
            final DataDirectoryValidator validator =
                dataDirectoryValidator != null ? dataDirectoryValidator : brokerCopier::validate;
            yield disableVersionedDirectory
                ? new NodeIdBasedDataDirectoryProvider(nodeInstance)
                : new VersionedNodeIdBasedDataDirectoryProvider(
                    objectMapper,
                    nodeInstance,
                    brokerCopier::copy,
                    validator,
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
