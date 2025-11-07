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

  private final Cluster cluster;

  @Autowired
  public NodeIdProviderConfiguration(final UnifiedConfiguration configuration) {
    cluster = configuration.getCamunda().getCluster();
  }

  @Bean
  /** Create the S3NodeReposiotry as a separate bean so it's lifecycle is managed by spring */
  public Optional<S3NodeIdRepository> s3NodeIdRepository() {
    return switch (cluster.getDynamicNodeId().getType()) {
      case NONE -> Optional.empty();
      case S3 -> {
        final var clientConfig = makeS3ClientConfig(cluster.getDynamicNodeId().s3());
        final var config =
            new S3NodeIdRepository.Config(
                cluster.getDynamicNodeId().s3().getBucketName(),
                cluster.getDynamicNodeId().s3().getLeaseDuration());
        yield Optional.of(S3NodeIdRepository.of(clientConfig, config, Clock.systemUTC()));
      }
    };
  }

  @Bean
  public NodeIdProvider nodeIdProvider(final Optional<NodeIdRepository> nodeIdRepository) {
    return switch (cluster.getDynamicNodeId().getType()) {
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
        yield new RepositoryNodeIdProvider(
            nodeIdRepository.get(),
            Clock.systemUTC(),
            config.getLeaseDuration(),
            UUID.randomUUID().toString(),
            () -> System.exit(-1));
      }
    };
  }

  private static S3ClientConfig makeS3ClientConfig(final S3 s3) {
    Optional<S3ClientConfig.Credentials> credentials = Optional.empty();
    if (s3.getAccessKey() != null && s3.getSecretKey() != null) {
      credentials =
          Optional.of(new S3ClientConfig.Credentials(s3.getAccessKey(), s3.getSecretKey()));
    }
    return new S3ClientConfig(
        credentials,
        Optional.ofNullable(s3.getRegion()).map(Region::of),
        Optional.ofNullable(s3.getEndpoint()).map(URI::create),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.ofNullable(s3.getApiCallTimeout()));
  }
}
