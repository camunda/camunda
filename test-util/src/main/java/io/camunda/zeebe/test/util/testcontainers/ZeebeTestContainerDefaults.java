/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.testcontainers;

import java.util.Optional;
import org.testcontainers.utility.DockerImageName;

public final class ZeebeTestContainerDefaults {
  private static final String TEST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("ZEEBE_TEST_DOCKER_IMAGE"))
          .orElse("camunda/zeebe:current-test");
  private static final DockerImageName DEFAULT_TEST_IMAGE = DockerImageName.parse(TEST_IMAGE_NAME);

  private ZeebeTestContainerDefaults() {}

  public static DockerImageName defaultTestImage() {
    return DEFAULT_TEST_IMAGE;
  }
}
