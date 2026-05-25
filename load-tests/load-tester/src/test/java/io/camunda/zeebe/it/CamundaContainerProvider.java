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
 * <p>Image resolves from {@code -Dcamunda.docker.test.image}, then {@code
 * CAMUNDA_TEST_DOCKER_IMAGE}, else defaults to {@code camunda/camunda:SNAPSHOT} (moving Docker Hub
 * tag from main-branch CI). Override on stable/X.Y branches or when using a locally built image.
 */
final class CamundaContainerProvider {

  static final String IMAGE_SYSPROP = "camunda.docker.test.image";
  static final String IMAGE_ENV_VAR = "CAMUNDA_TEST_DOCKER_IMAGE";
  static final String DEFAULT_IMAGE = "camunda/camunda:SNAPSHOT";

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    return new CamundaContainer(DockerImageName.parse(resolveImage()))
        .withImagePullPolicy(PullPolicy.ageBased(Duration.ofHours(12)));
  }

  static String resolveImage() {
    return Optional.ofNullable(System.getProperty(IMAGE_SYSPROP))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .or(
            () ->
                Optional.ofNullable(System.getenv(IMAGE_ENV_VAR))
                    .map(String::trim)
                    .filter(s -> !s.isBlank()))
        .orElse(DEFAULT_IMAGE);
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.rest-address", camunda::getRestApiAddress);
  }
}
