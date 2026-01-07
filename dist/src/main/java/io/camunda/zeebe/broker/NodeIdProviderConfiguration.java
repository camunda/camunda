/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.BrokerDataDirectoryCopier;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.dynamic.nodeid.ConfiguredDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.DataDirectoryCopier;
import io.camunda.zeebe.dynamic.nodeid.DataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RepositoryNodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.VersionedNodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import java.io.IOException;
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

  private final ObjectMapper objectMapper;
  private final Cluster cluster;

  @Autowired
  public NodeIdProviderConfiguration(
      final ObjectMapper objectMapper, final UnifiedConfiguration configuration) {
    this.objectMapper = objectMapper;
    cluster = configuration.getCamunda().getCluster();
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
                s3Config.getNodeIdMappingUpdateTimeout());
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
      final BrokerBasedConfiguration brokerBasedConfiguration) {
    final DataCfg data = brokerBasedConfiguration.config().getData();
    final var initializer =
        switch (data.getInitializationMode()) {
          case USE_PRECONFIGURED_DIRECTORY -> new ConfiguredDataDirectoryProvider();
          case SHARED_ROOT_NODE -> new NodeIdBasedDataDirectoryProvider(nodeIdProvider);
          case SHARED_ROOT_VERSIONED_NODE -> {
            final var copier = new BrokerDataDirectoryCopier();
            yield new VersionedNodeIdBasedDataDirectoryProvider(
                objectMapper, nodeIdProvider.currentNodeInstance(), fromBrokerCopier(copier));
          }
        };

    // Use the configured data root directory as the root input; for versioned layouts this is
    // expected to be a shared root which we then derive a nodeId/version-specific effective
    // directory from.
    final Path configuredRootDirectory = Path.of(data.getRootDirectory());
    final var configuredDirectory = initializer.initialize(configuredRootDirectory).join();
    data.setDirectory(configuredDirectory.toString());

    return initializer;
  }

  // brokerCopier does not implement the interface because it's defined in zeebe-broker module
  // which does not depend on dynamic-node-id-provider module.
  private DataDirectoryCopier fromBrokerCopier(final BrokerDataDirectoryCopier brokerCopier) {
    return new DataDirectoryCopier() {
      @Override
      public void copy(final Path source, final Path target, final String markerFileName)
          throws IOException {
        brokerCopier.copy(source, target, markerFileName);
      }

      @Override
      public void validate(final Path source, final Path target, final String markerFileName)
          throws IOException {
        brokerCopier.validate(source, target, markerFileName);
      }
    };
  }
}
