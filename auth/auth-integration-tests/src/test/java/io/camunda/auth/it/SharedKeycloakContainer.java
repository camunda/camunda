/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.time.Duration;

/**
 * Singleton Keycloak container shared across grant type integration tests. Starts once on first
 * access; JVM shutdown hook handles cleanup. Each IT creates its own realm to avoid collisions.
 *
 * <p>Pinned to Keycloak 26.5.4 which supports RFC 8693 standard token exchange.
 */
final class SharedKeycloakContainer {

  private static final KeycloakContainer INSTANCE;

  static {
    INSTANCE =
        new KeycloakContainer("quay.io/keycloak/keycloak:26.5.4")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1")
            .withFeaturesEnabled("token-exchange", "admin-fine-grained-authz");
    INSTANCE.start();
  }

  private SharedKeycloakContainer() {}

  static KeycloakContainer getInstance() {
    return INSTANCE;
  }
}
