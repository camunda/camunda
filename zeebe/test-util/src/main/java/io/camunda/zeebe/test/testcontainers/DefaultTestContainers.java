/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Duration;
import org.testcontainers.utility.DockerImageName;

public final class DefaultTestContainers {
  // Keep in sync with version.keycloak.container in parent/pom.xml
  private static final DockerImageName KEYCLOAK_IMAGE =
      DockerImageName.parse("quay.io/keycloak/keycloak").withTag("26.7.3");

  private DefaultTestContainers() {}

  /** Returns a Keycloak container with defaults for CI. */
  public static KeycloakContainer createDefaultKeycloak() {
    final var container =
        new KeycloakContainer(KEYCLOAK_IMAGE)
            // Keycloak can take quite a while to start in CI
            .withStartupTimeout(Duration.ofMinutes(5))
            // speed up startup time at the expense of slower runtime, acceptable in CI
            .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1");

    // remove the default log consumer
    container.getLogConsumers().clear();

    return container;
  }
}
