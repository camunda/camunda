/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.testcontainers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Duration;

public final class DefaultTestContainers {

  private DefaultTestContainers() {}

  /** Returns a Keycloak container with defaults for CI. */
  public static KeycloakContainer createDefaultKeycloak() {
    final var container =
        new KeycloakContainer()
            // Keycloak can take quite a while to start in CI
            .withStartupTimeout(Duration.ofMinutes(5))
            // speed up startup time at the expense of slower runtime, acceptable in CI
            .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1");

    // remove the default log consumer
    container.getLogConsumers().clear();

    return container;
  }
}
