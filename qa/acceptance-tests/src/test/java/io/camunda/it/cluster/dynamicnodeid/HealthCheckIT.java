/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository.S3ClientConfig;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry.ContainerProxy;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@ZeebeIntegration
public class HealthCheckIT {

  @Container
  private static final ToxiproxyContainer TOXIPROXY =
      ProxyRegistry.addExposedPorts(
          new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0").withAccessToHost(true));

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withAccessToHost(true)
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client s3Client;
  private static final String BUCKET_NAME = UUID.randomUUID().toString();
  private static final int CLUSTER_SIZE = 3;
  private static final Duration LEASE_DURATION = Duration.ofSeconds(5);
  private static final String TASK_ID_PROPERTY = "camunda.cluster.node-id-provider.s3.taskId";
  private static final ProxyRegistry PROXY_REGISTRY = new ProxyRegistry(TOXIPROXY);
  private static URI toxiproxyS3Endpoint;
  private static ContainerProxy proxy;

  @TestZeebe
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAdditionalProperties(
              Map.of(
                  "camunda.data.secondary-storage.type",
                  "none",
                  "camunda.cluster.size",
                  CLUSTER_SIZE))
          // S3 repository properties
          .withAdditionalProperties(
              Map.of(
                  "camunda.cluster.node-id-provider.type",
                  "s3",
                  TASK_ID_PROPERTY,
                  UUID.randomUUID().toString(),
                  "camunda.cluster.node-id-provider.s3.bucketName",
                  BUCKET_NAME,
                  "camunda.cluster.node-id-provider.s3.leaseDuration",
                  LEASE_DURATION,
                  "camunda.cluster.node-id-provider.s3.endpoint",
                  toxiproxyS3Endpoint,
                  "camunda.cluster.node-id-provider.s3.region",
                  S3.getRegion(),
                  "camunda.cluster.node-id-provider.s3.accessKey",
                  S3.getAccessKey(),
                  "camunda.cluster.node-id-provider.s3.secretKey",
                  S3.getSecretKey(),
                  "camunda.cluster.node-id-provider.s3.apiCallTimeout",
                  LEASE_DURATION.dividedBy(2).toString()));

  @BeforeAll
  public static void setupAll() throws URISyntaxException {
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3ClientConfig(
                Optional.of(new S3ClientConfig.Credentials(S3.getAccessKey(), S3.getSecretKey())),
                Optional.of(Region.of(S3.getRegion())),
                Optional.of(S3.getEndpoint())));

    // bucket must be created before the application is started
    s3Client.createBucket(b -> b.bucket(BUCKET_NAME));

    proxy = PROXY_REGISTRY.getOrCreateHostProxy(S3.getEndpoint().getPort());

    // Get the Toxiproxy endpoint
    final var s3Endpoint = S3.getEndpoint();
    toxiproxyS3Endpoint =
        new URI(
            s3Endpoint.getScheme(),
            s3Endpoint.getUserInfo(),
            "0.0.0.0",
            TOXIPROXY.getMappedPort(proxy.internalPort()),
            s3Endpoint.getPath(),
            s3Endpoint.getPath(),
            s3Endpoint.getFragment());
  }

  @Test
  public void shouldNotReturnHealthyWhenS3IsNotAvailable() throws IOException {
    final var actuator = BrokerHealthActuator.of(broker);
    assertThatNoException().isThrownBy(actuator::ready);
    // When:
    // - toxiproxy introduces 10s latency in the HTTP calls
    // - broker tries to check health & s3 is not able to reply in time
    proxy.proxy().toxics().latency("latency", ToxicDirection.UPSTREAM, 10000L);
    // Then: health check should fail
    Awaitility.await("Until actuator" + actuator)
        .untilAsserted(() -> assertThatThrownBy(actuator::ready).isInstanceOf(Exception.class));
  }
}
