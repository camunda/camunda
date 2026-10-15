/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Duplicates {@code io.camunda.zeebe.test.testcontainers.DefaultTestContainers}, which this module
 * cannot depend on: {@code zeebe-test-util} depends on {@code camunda-client-java}.
 */
final class KeycloakContainers {

  private static final String VERSIONS_FILE = "/client-java-testcontainers.properties";

  private static final String KEYCLOAK_IMAGE = keycloakImage();

  private KeycloakContainers() {}

  /** Returns a Keycloak container with defaults for CI. */
  static KeycloakContainer createDefaultKeycloak() {
    return new KeycloakContainer(KEYCLOAK_IMAGE)
        // Keycloak can take quite a while to start in CI
        .withStartupTimeout(Duration.ofMinutes(5))
        // speed up startup time at the expense of slower runtime, acceptable in CI
        .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1");
  }

  /**
   * Returns the pinned Keycloak image, which Maven resource filtering writes into {@value
   * #VERSIONS_FILE} from the {@code version.keycloak.container} property in {@code parent/pom.xml}.
   */
  private static String keycloakImage() {
    final Properties properties = new Properties();
    try (final InputStream in = KeycloakContainers.class.getResourceAsStream(VERSIONS_FILE)) {
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
