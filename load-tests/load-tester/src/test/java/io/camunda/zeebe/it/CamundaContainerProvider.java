/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.time.Duration;
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

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    // CI passes -Dio.camunda.process.test.camundaDockerImage{Name,Version} to point at the image
    // built locally by build-platform-docker in the same job. Honor those first; otherwise fall
    // back to the bundled defaults (resolved from camunda-process-test-java's filtered properties
    // and git.branch). Without this, the load-tester module is not in the verify -pl reactor, so
    // the filtered properties never get rewritten and ITs pull camunda/camunda:SNAPSHOT, which is
    // a head-of-main build incompatible with stable/8.x.
    final String imageName =
        System.getProperty(
            "io.camunda.process.test.camundaDockerImageName",
            CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME);
    final String imageVersion =
        System.getProperty(
            "io.camunda.process.test.camundaDockerImageVersion",
            CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION);
    return new CamundaContainer(DockerImageName.parse(imageName).withTag(imageVersion));
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.rest-address", camunda::getRestApiAddress);
  }
}
