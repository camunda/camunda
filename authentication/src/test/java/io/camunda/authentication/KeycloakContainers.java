/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Duplicates {@code io.camunda.zeebe.test.testcontainers.DefaultTestContainers}, which this module
 * would have to depend on {@code zeebe-test-util} to reach.
 */
final class KeycloakContainers {
  private static final String VERSIONS_FILE = "/authentication-testcontainers.properties";

  private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:" + keycloakVersion();

  private KeycloakContainers() {}

  /** Returns a Keycloak container with defaults for CI. */
  static KeycloakContainer createDefaultKeycloak() {
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

  /**
   * Returns the pinned Keycloak image tag, which Maven resource filtering writes into {@value
   * #VERSIONS_FILE} from the {@code version.keycloak.container} property in {@code parent/pom.xml}.
   */
  private static String keycloakVersion() {
    final Properties properties = new Properties();
    try (final InputStream in = KeycloakContainers.class.getResourceAsStream(VERSIONS_FILE)) {
      if (in == null) {
        throw new IllegalStateException(VERSIONS_FILE + " is not on the classpath");
      }
      properties.load(in);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read " + VERSIONS_FILE, e);
    }

    final String version = properties.getProperty("keycloak.version");
    if (version == null || version.startsWith("${")) {
      // unresolved placeholder: the sources were used without Maven having filtered them
      throw new IllegalStateException(
          "keycloak.version in " + VERSIONS_FILE + " is unresolved; run a Maven build first");
    }

    return version;
  }
}
