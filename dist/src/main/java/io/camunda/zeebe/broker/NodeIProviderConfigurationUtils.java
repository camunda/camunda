/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.configuration.Cluster;
import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.zeebe.broker.system.BrokerDataDirectoryCopier;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RepositoryNodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryCopier;
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
import software.amazon.awssdk.regions.Region;

/**
 * Utility class to create NodeIdProvider related objects based on the configuration.
 *
 * <p>Used by both the broker and the restore application.
 */
public class NodeIProviderConfigurationUtils {
  public static S3NodeIdRepository getS3NodeIdRepository(final Cluster cluster) {
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

  public static NodeIdProvider getNodeIdProvider(
      final Logger log,
      final Cluster cluster,
      final Optional<NodeIdRepository> nodeIdRepository,
      final Runnable shutdownHelper) {
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
            log.debug("Node configured with taskId {}", taskId);
            yield new RepositoryNodeIdProvider(
                nodeIdRepository.get(),
                Clock.systemUTC(),
                config.getLeaseDuration(),
                config.getLeaseAcquireMaxDelay(),
                config.getReadinessCheckTimeout(),
                taskId,
                () -> {
                  log.warn("NodeIdProvider terminating the process");
                  shutdownHelper.run();
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

  // brokerCopier does not implement the interface because it's defined in zeebe-broker module
  // which does not depend on dynamic-node-id-provider module.
  public static DataDirectoryCopier fromBrokerCopier(final BrokerDataDirectoryCopier brokerCopier) {
    return new DataDirectoryCopier() {
      @Override
      public void copy(
          final Path source,
          final Path target,
          final String markerFileName,
          final boolean useHardLinks)
          throws IOException {
        brokerCopier.copy(source, target, markerFileName, useHardLinks);
      }

      @Override
      public void validate(final Path source, final Path target, final String markerFileName)
          throws IOException {
        brokerCopier.validate(source, target, markerFileName);
      }
    };
  }
}
