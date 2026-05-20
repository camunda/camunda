/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import io.camunda.process.test.impl.containers.CamundaContainer;
import java.time.Duration;
import java.util.Optional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared utilities for creating and configuring a {@link CamundaContainer} in integration tests.
 *
 * <p>Image tag resolution: {@code -Dcamunda.docker.test.image} → {@code CAMUNDA_TEST_DOCKER_IMAGE}
 * env var → {@code camunda/camunda:current-test}. Mirrors {@code dist/AbstractCamundaDockerIT} and
 * {@code ZeebeTestContainerDefaults} so existing CI plumbing applies.
 */
final class CamundaContainerProvider {

  static final String IMAGE_SYSPROP = "camunda.docker.test.image";
  static final String IMAGE_ENV_VAR = "CAMUNDA_TEST_DOCKER_IMAGE";
  static final String DEFAULT_IMAGE = "camunda/camunda:current-test";

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    return new CamundaContainer(DockerImageName.parse(resolveImage()))
        .withImagePullPolicy(PullPolicy.ageBased(Duration.ofHours(12)));
  }

  static String resolveImage() {
    return Optional.ofNullable(System.getProperty(IMAGE_SYSPROP))
        .filter(s -> !s.isEmpty())
        .or(() -> Optional.ofNullable(System.getenv(IMAGE_ENV_VAR)).filter(s -> !s.isEmpty()))
        .orElse(DEFAULT_IMAGE);
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.rest-address", camunda::getRestApiAddress);
  }
}
