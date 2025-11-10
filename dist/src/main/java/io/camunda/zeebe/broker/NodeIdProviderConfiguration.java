/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.configuration.Cluster;
import io.camunda.configuration.DynamicNodeIdConfig.S3;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.RepositoryNodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker", "restore"})
@DependsOn("unifiedConfigurationHelper")
public class NodeIdProviderConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(NodeIdProviderConfiguration.class);

  private final Cluster cluster;

  @Autowired
  public NodeIdProviderConfiguration(final UnifiedConfiguration configuration) {
    cluster = configuration.getCamunda().getCluster();
  }

  @Bean
  /** Create the S3NodeReposiotry as a separate bean so it's lifecycle is managed by spring */
  public S3NodeIdRepository s3NodeIdRepository() {
    return switch (cluster.getDynamicNodeId().getType()) {
      case NONE -> null;
      case S3 -> {
        final var clientConfig = makeS3ClientConfig(cluster.getDynamicNodeId().s3());
        final var config =
            new S3NodeIdRepository.Config(
                cluster.getDynamicNodeId().s3().getBucketName(),
                cluster.getDynamicNodeId().s3().getLeaseDuration());
        yield S3NodeIdRepository.of(clientConfig, config, Clock.systemUTC());
      }
    };
  }

  @Bean
  public NodeIdProvider nodeIdProvider(final Optional<NodeIdRepository> nodeIdRepository) {
    final var nodeIdProvider =
        switch (cluster.getDynamicNodeId().getType()) {
          case NONE -> {
            final var nodeId = cluster.getNodeId();
            yield NodeIdProvider.staticProvider(nodeId);
          }
          case S3 -> {
            final var config = cluster.getDynamicNodeId().s3();
            if (nodeIdRepository.isEmpty()) {
              throw new IllegalStateException(
                  "DynamicNodeIdProvider configured to use S3: missing s3 node id repository");
            }
            final var taskId = config.getTaskId().orElse(UUID.randomUUID().toString());
            LOG.info("Node configured with taskId {}", taskId);
            final var repository =
                new RepositoryNodeIdProvider(
                    nodeIdRepository.get(),
                    Clock.systemUTC(),
                    config.getLeaseDuration(),
                    taskId,
                    () -> System.exit(-1));
            repository.initialize(cluster.getSize());
            yield repository;
          }
        };
    nodeIdProvider.initialize(cluster.getSize());

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
}
