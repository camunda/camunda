/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

public final class DefaultTestContainers {
  private static final String VERSIONS_FILE = "/zeebe-test-util-testcontainers.properties";

  private static final String KEYCLOAK_IMAGE = keycloakImage();

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

  /**
   * Returns the pinned Keycloak image, which Maven resource filtering writes into {@value
   * #VERSIONS_FILE} from the {@code version.keycloak.container} property in {@code parent/pom.xml}.
   */
  private static String keycloakImage() {
    final Properties properties = new Properties();
    try (final InputStream in = DefaultTestContainers.class.getResourceAsStream(VERSIONS_FILE)) {
      if (in == null) {
        throw new IllegalStateException(VERSIONS_FILE + " is not on the classpath");
      }
      properties.load(in);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read " + VERSIONS_FILE, e);
    }

    final String image = properties.getProperty("keycloak.image");
    if (image == null || image.contains("${")) {
      // unresolved placeholder: the sources were used without Maven having filtered them
      throw new IllegalStateException(
          "keycloak.image in " + VERSIONS_FILE + " is unresolved; run a Maven build first");
    }

    return image;
  }
}
