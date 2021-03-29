/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.testcontainers;

import org.testcontainers.utility.DockerImageName;

public final class ZeebeTestContainerDefaults {
  private static final DockerImageName DEFAULT_TEST_IMAGE =
      DockerImageName.parse("camunda/zeebe:current-test");

  private ZeebeTestContainerDefaults() {}

  public static DockerImageName defaultTestImage() {
    return DEFAULT_TEST_IMAGE;
  }
}
