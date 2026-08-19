/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.ConfigurationUtil;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;

/**
 * Verifies that, with a dynamic (S3) node id provider configured, a broker serving more than one
 * physical tenant actually writes each tenant's raft partition data on disk under the same
 * node-level, node-id-resolved directory root — not just the default tenant's.
 */
@Testcontainers
@ZeebeIntegration
final class DynamicNodeIdPhysicalTenantIT {

  private static final String TENANT_A = "tenanta";
  private static final String BUCKET_NAME = UUID.randomUUID().toString();
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withUnifiedConfig(
              cfg -> {
                cfg.getCluster().getNodeIdProvider().setType(Type.S3);
                final var s3 = cfg.getCluster().getNodeIdProvider().s3();
                s3.setTaskId(UUID.randomUUID().toString());
                s3.setBucketName(BUCKET_NAME);
                s3.setLeaseDuration(LEASE_DURATION);
                s3.setEndpoint(S3.getEndpoint().toString());
                s3.setRegion(S3.getRegion());
                s3.setAccessKey(S3.getAccessKey());
                s3.setSecretKey(S3.getSecretKey());
              })
          .withPtConfig(
              TENANT_A,
              camunda ->
                  camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.none));

  @BeforeAll
  static void setupAll() {
    try (final var s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())))) {
      // the bucket must exist before the application starts
      s3Client.createBucket(b -> b.bucket(BUCKET_NAME));
    }
  }

  @Test
  void shouldWritePartitionDataForBothTenantsUnderTheSharedNodeDirectory() throws IOException {
    // given - the node-level directory the dynamic node id provider resolved for this broker,
    // computed independently from the broker's raw root configuration and its leased node id -
    // not read from any per-physical-tenant config
    final var sharedNodeDirectory = getSharedNodeDirectory(broker);

    // when - locating each physical tenant's own partition-1 directory under that shared root
    final var defaultPartitionDirectory =
        RaftPartitionFactory.getPartitionDirectory(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
            sharedNodeDirectory.toString());
    final var tenantAPartitionDirectory =
        RaftPartitionFactory.getPartitionDirectory(
            new PartitionId(TENANT_A, 1), sharedNodeDirectory.toString());

    // then - both physical tenants actually wrote their own raft log segment files under that
    // same, node-id-resolved shared root, rather than under a directory each independently
    // derived from its own raw configuration
    assertThat(segmentFiles(defaultPartitionDirectory))
        .as("default tenant's raft log segment files under %s", defaultPartitionDirectory)
        .isNotEmpty();
    assertThat(segmentFiles(tenantAPartitionDirectory))
        .as("%s's raft log segment files under %s", TENANT_A, tenantAPartitionDirectory)
        .isNotEmpty();
  }

  private static Path getSharedNodeDirectory(final TestStandaloneBroker broker) {
    final var rootDirectory =
        Path.of(
            ConfigurationUtil.toAbsolutePath(
                broker.unifiedConfig().getData().getPrimaryStorage().getDirectory(),
                broker.getWorkingDirectory().toString()));
    final int nodeId = broker.bean(BrokerBasedProperties.class).getCluster().getNodeId();
    // a fresh, never-restarted broker is always on version 1 of its versioned node directory
    return rootDirectory.resolve("node-" + nodeId).resolve("v1");
  }

  private static List<Path> segmentFiles(final Path partitionDirectory) throws IOException {
    if (!Files.isDirectory(partitionDirectory)) {
      return List.of();
    }
    try (final var files = Files.list(partitionDirectory)) {
      return files.filter(path -> path.toString().endsWith(".log")).toList();
    }
  }
}
