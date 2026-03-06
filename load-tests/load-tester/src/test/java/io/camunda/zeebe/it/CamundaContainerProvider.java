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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared utilities for creating and configuring a {@link CamundaContainer} in integration tests.
 */
final class CamundaContainerProvider {

  private CamundaContainerProvider() {}

  static CamundaContainer createCamundaContainer() {
    return new CamundaContainer(
        DockerImageName.parse(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME)
            .withTag(CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_VERSION));
  }

  static void registerClientProperties(
      final CamundaContainer camunda, final DynamicPropertyRegistry registry) {
    registry.add("camunda.client.grpc-address", camunda::getGrpcApiAddress);
    registry.add("camunda.client.rest-address", camunda::getRestApiAddress);
  }
}
