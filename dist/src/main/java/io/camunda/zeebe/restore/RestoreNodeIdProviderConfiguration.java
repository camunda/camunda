/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.fromBrokerCopier;
import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.getNodeIdProvider;
import static io.camunda.zeebe.broker.NodeIProviderConfigurationUtils.getS3NodeIdRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.BrokerDataDirectoryCopier;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RestoreStatusManager;
import io.camunda.zeebe.dynamic.nodeid.fs.ConfiguredDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.NodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.VersionedNodeIdBasedDataDirectoryProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.restore.RestoreApp.PostRestoreAction;
import io.camunda.zeebe.restore.RestoreApp.PostRestoreActionContext;
import io.camunda.zeebe.restore.RestoreApp.PreRestoreAction;
import io.camunda.zeebe.restore.RestoreApp.PreRestoreActionResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"restore"})
@DependsOn("unifiedConfigurationHelper")
public class RestoreNodeIdProviderConfiguration {
  private static final Logger LOG =
      LoggerFactory.getLogger(RestoreNodeIdProviderConfiguration.class);
  @Autowired private ApplicationContext appContext;
  private final Cluster cluster;
  private final boolean disableVersionedDirectory;
  private final ObjectMapper objectMapper;

  @Autowired
  public RestoreNodeIdProviderConfiguration(
      final UnifiedConfiguration configuration, final ObjectMapper objectMapper) {
    cluster = configuration.getCamunda().getCluster();
    final var primaryStorage = configuration.getCamunda().getData().getPrimaryStorage();
    disableVersionedDirectory = primaryStorage.disableVersionedDirectory();
    this.objectMapper = objectMapper;
  }

  @Bean
  /** Create the S3NodeReposiotry as a separate bean so it's lifecycle is managed by spring */
  public S3NodeIdRepository s3NodeIdRepository() {
    return getS3NodeIdRepository(cluster);
  }

  @Bean
  public PreRestoreAction preRestoreAction(final Optional<NodeIdRepository> nodeIdRepository) {
    return switch (cluster.getNodeIdProvider().getType()) {
      case FIXED -> (restoreId, ignore) -> new PreRestoreActionResult(false, "");
      case S3 -> {
        if (nodeIdRepository.isEmpty()) {
          throw new IllegalStateException(
              "PreRestoreAction configured to use S3: missing s3 node id repository");
        }
        final var restoreStatusManager = new RestoreStatusManager(nodeIdRepository.get());
        yield ((restoreId, nodeId) -> {
          final var restoreStatus = restoreStatusManager.initializeRestore(restoreId);
          if (restoreStatus.isNodeRestored(nodeId)) {
            return new PreRestoreActionResult(
                true, "Node " + nodeId + " has already been restored for restore id " + restoreId);
          } else {
            return new PreRestoreActionResult(false, "");
          }
        });
      }
    };
  }

  @Bean
  public PostRestoreAction postRestoreAction(
      final Optional<NodeIdRepository> nodeIdRepository, final BrokerBasedProperties brokerCfg) {
    return switch (cluster.getNodeIdProvider().getType()) {
      case FIXED -> (context) -> validateAfterRestore(brokerCfg, context);
      case S3 -> {
        if (nodeIdRepository.isEmpty()) {
          throw new IllegalStateException(
              "PostRestoreAction configured to use S3: missing s3 node id repository");
        }
        final var restoreStatusManager = new RestoreStatusManager(nodeIdRepository.get());
        yield (context -> {
          final var restoreId = context.restoreId();
          final var nodeId = context.nodeId();
          // Validate even if we skipped in case the restore was retried with empty disk, but the s3
          // object was not deleted.
          validateAfterRestore(brokerCfg, context);

          if (!context.skippedRestore()) {
            restoreStatusManager.markNodeRestored(restoreId, nodeId);
          }

          restoreStatusManager.waitForAllNodesRestored(
              restoreId, cluster.getSize(), Duration.ofSeconds(10));
        });
      }
    };
  }

  private static void validateAfterRestore(
      final BrokerBasedProperties brokerCfg, final PostRestoreActionContext context) {
    if (!RestoreValidator.validate(brokerCfg)) {
      final String message;
      if (context.skippedRestore()) {
        message =
            String.format(
                "Expected to find restored data on node %d, but validation failed. Possible root cause: Restore is skipped because node is marked as restored in the s3 bucket, but node started with empty directory. Try restoring the cluster after deleting the s3 bucket configured for node id provider.",
                context.nodeId());
      } else {
        message =
            String.format(
                "Expected to find restored data on node %d, but validation failed.",
                context.nodeId());
      }
      throw new IllegalStateException(message);
    }
  }

  @Bean
  public NodeIdProvider nodeIdProvider(final Optional<NodeIdRepository> nodeIdRepository) {
    return getNodeIdProvider(
        LOG, cluster, nodeIdRepository, () -> SpringApplication.exit(appContext, () -> 1));
  }

  @Bean
  public DataDirectoryProvider dataDirectoryProvider(
      final NodeIdProvider nodeIdProvider,
      final BrokerBasedProperties brokerBasedProperties,
      final WorkingDirectory workingDirectory) {

    // The working directory must be initialized before we initialize the data directory
    brokerBasedProperties.init(workingDirectory.path().toAbsolutePath().toString());

    final var initializer =
        switch (cluster.getNodeIdProvider().getType()) {
          case FIXED -> new ConfiguredDataDirectoryProvider();
          case S3 -> {
            final var brokerCopier = new BrokerDataDirectoryCopier();
            final var nodeInstance = nodeIdProvider.currentNodeInstance();
            yield disableVersionedDirectory
                ? new NodeIdBasedDataDirectoryProvider(nodeInstance)
                // We use the versioned provider, even though we expect the directory to be empty.
                // If the directory is not empty, the new versioned directory will contain the
                // previous version's data. This helps to handle this scenario consistently in the
                // restore app if we are restoring to a node that had previous data.
                // retention count does not matter, as we are creating a new directory for restore.
                : new VersionedNodeIdBasedDataDirectoryProvider(
                    objectMapper, nodeInstance, fromBrokerCopier(brokerCopier), true, 2);
          }
        };

    final DataCfg data = brokerBasedProperties.getData();
    final var directory = Path.of(data.getDirectory());
    final var configuredDirectory = initializer.initialize(directory).join();
    data.setDirectory(configuredDirectory.toString());

    return initializer;
  }
}
