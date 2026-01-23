/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RepositoryNodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RestoreStatusManager;
import io.camunda.zeebe.dynamic.nodeid.fs.ConfiguredDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.NodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.UnInitializedVersionedNodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.restore.RestoreApp.PostRestoreAction;
import io.camunda.zeebe.restore.RestoreApp.PreRestoreAction;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"restore"})
@DependsOn("unifiedConfigurationHelper")
public class NodeIdProviderConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(NodeIdProviderConfiguration.class);
  @Autowired private ApplicationContext appContext;
  private final Cluster cluster;
  private final boolean disableVersionedDirectory;

  @Autowired
  public NodeIdProviderConfiguration(
      final UnifiedConfiguration configuration, final ObjectMapper objectMapper) {
    cluster = configuration.getCamunda().getCluster();
    final var primaryStorage = configuration.getCamunda().getData().getPrimaryStorage();
    disableVersionedDirectory = primaryStorage.disableVersionedDirectory();
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
  public PreRestoreAction preRestoreAction(final Optional<NodeIdRepository> nodeIdRepository) {
    return switch (cluster.getNodeIdProvider().getType()) {
      case FIXED -> (ignoreId, ignore) -> {};
      case S3 -> {
        if (nodeIdRepository.isEmpty()) {
          throw new IllegalStateException(
              "PreRestoreAction configured to use S3: missing s3 node id repository");
        }
        final var restoreStatusManager = new RestoreStatusManager(nodeIdRepository.get());
        yield ((backupId, nodeId) -> restoreStatusManager.initializeRestore(backupId));
      }
    };
  }

  @Bean
  public PostRestoreAction postRestoreAction(final Optional<NodeIdRepository> nodeIdRepository) {
    return switch (cluster.getNodeIdProvider().getType()) {
      case FIXED -> (ignoreId, ignore) -> {};
      case S3 -> {
        if (nodeIdRepository.isEmpty()) {
          throw new IllegalStateException(
              "PostRestoreAction configured to use S3: missing s3 node id repository");
        }
        final var restoreStatusManager = new RestoreStatusManager(nodeIdRepository.get());
        yield ((backupId, nodeId) -> {
          restoreStatusManager.markNodeRestored(nodeId);

          restoreStatusManager.waitForAllNodesRestored(cluster.getSize(), Duration.ofSeconds(10));
        });
      }
    };
  }

  @Bean
  public NodeIdProvider nodeIdProvider(final Optional<NodeIdRepository> nodeIdRepository) {
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
                  SpringApplication.exit(appContext, () -> 1);
                  ;
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
      final BrokerBasedProperties brokerBasedConfiguration,
      final WorkingDirectory workingDirectory) {

    brokerBasedConfiguration.init(workingDirectory.path().toAbsolutePath().toString());

    final var initializer =
        switch (cluster.getNodeIdProvider().getType()) {
          case FIXED -> new ConfiguredDataDirectoryProvider();
          case S3 -> {
            final var nodeInstance = nodeIdProvider.currentNodeInstance();
            yield disableVersionedDirectory
                ? new NodeIdBasedDataDirectoryProvider(nodeInstance)
                : new UnInitializedVersionedNodeIdBasedDataDirectoryProvider(nodeInstance);
          }
        };

    final DataCfg data = brokerBasedConfiguration.getData();
    final var directory = Path.of(data.getDirectory());
    final var configuredDirectory = initializer.initialize(directory).join();
    data.setDirectory(configuredDirectory.toString());

    return initializer;
  }
}
