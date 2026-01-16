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
import io.camunda.configuration.NodeIdProvider.Type;
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
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
@Execution(ExecutionMode.SAME_THREAD)
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
  private static final Duration LEASE_DURATION = Duration.ofSeconds(5);
  private static final ProxyRegistry PROXY_REGISTRY = new ProxyRegistry(TOXIPROXY);
  private static URI toxiproxyS3Endpoint;
  private static ContainerProxy proxy;
  private static final String LATENCY_TOXIC = "latency";

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnifiedConfig(
              cfg -> {
                cfg.getCluster().getNodeIdProvider().setType(Type.S3);
                final var s3 = cfg.getCluster().getNodeIdProvider().s3();
                s3.setTaskId(UUID.randomUUID().toString());
                s3.setBucketName(BUCKET_NAME);
                s3.setLeaseDuration(LEASE_DURATION);
                s3.setEndpoint(toxiproxyS3Endpoint.toString());
                s3.setRegion(S3.getRegion());
                s3.setAccessKey(S3.getAccessKey());
                s3.setSecretKey(S3.getSecretKey());
                s3.setApiCallTimeout(LEASE_DURATION.dividedBy(2));
              });

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
            s3Endpoint.getQuery(),
            s3Endpoint.getFragment());
  }

  @AfterEach
  public void cleanUp() throws IOException {
    final var toxic = proxy.proxy().toxics().get(LATENCY_TOXIC);
    if (toxic != null) {
      toxic.remove();
    }
  }

  @Test
  public void shouldNotReturnHealthyWhenS3IsNotAvailable() throws IOException {
    final var actuator = BrokerHealthActuator.of(broker);
    assertThatNoException().isThrownBy(actuator::ready);
    // When:
    // - toxiproxy introduces 10s latency in the HTTP calls
    // - broker tries to check health & s3 is not able to reply in time
    proxy.proxy().toxics().latency(LATENCY_TOXIC, ToxicDirection.UPSTREAM, 10000L);
    // Then: health check should fail
    Awaitility.await("Until actuator" + actuator)
        .untilAsserted(() -> assertThatThrownBy(actuator::ready).isInstanceOf(Exception.class));
  }
}
