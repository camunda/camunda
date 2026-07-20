/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_DEFAULTHISTORYTTL;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared utilities for creating and configuring a {@link CamundaContainer} in integration tests.
 *
 * <p>Image resolution honors the standard {@value #IMAGE_NAME_PROPERTY} / {@value
 * #IMAGE_VERSION_PROPERTY} system properties at runtime, falling back to the values baked into
 * {@code camunda-process-test-java}'s filtered {@code git.properties}. Pass the sysprops to point
 * the IT at a specific image (e.g. a locally built {@code localhost:5000/camunda/camunda} tag from
 * an in-job registry); otherwise the bundled defaults are used.
 */
final class CamundaContainerProvider {

  private static final String IMAGE_NAME_PROPERTY =
      "io.camunda.process.test.camundaDockerImageName";
  private static final String IMAGE_VERSION_PROPERTY =
      "io.camunda.process.test.camundaDockerImageVersion";

  private static final Logger CONTAINER_LOG = LoggerFactory.getLogger("camunda.container");

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    final String imageName =
        System.getProperty(
            IMAGE_NAME_PROPERTY, CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME);
    final String imageVersion =
        System.getProperty(
            IMAGE_VERSION_PROPERTY, CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);
    return new CamundaContainer(DockerImageName.parse(imageName).withTag(imageVersion))
        .withImagePullPolicy(PullPolicy.ageBased(Duration.ofHours(12)))
        // capture container-side broker/exporter stdout so IT failures (indexing stalls,
        // backpressure, crashes, early history cleanup) are diagnosable from the test log
        .withLogConsumer(new Slf4jLogConsumer(CONTAINER_LOG).withPrefix("camunda"))
        // CamundaContainer defaults the RDBMS history TTL to PT2S and runs cleanup every 2-5s, so
        // completed process instances are purged within seconds of completion. These ITs query
        // completed instances after an async delay, which races that cleanup and flakes when the
        // runner is slow. Extend the TTL so instances survive the assertion window.
        .withEnv(CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_DEFAULTHISTORYTTL, "PT1H");
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.rest-address", camunda::getRestApiAddress);
  }
}
