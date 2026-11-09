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
package io.camunda.process.test.impl.containers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.ImageFromDockerfile;

/** Builds the image with Docker, from the Connectors image and from custom images. */
class ManagedScriptsImageIT {

  // The first version of the Connectors with managed scripts.
  private static final String CONNECTORS_IMAGE = "camunda/connectors-bundle:8.11-SNAPSHOT";

  private static final String PRINT_USER_AND_VERSIONS =
      "echo user=$(id -un); python3 --version; node --version";

  @Test
  void shouldInstallThePinnedVersionsInTheConnectorsImage() {
    // given
    final ManagedScriptsImage image = new ManagedScriptsImage(CONNECTORS_IMAGE, "3.12", "22");

    // when
    final String output = run(image.build(false));

    // then: the interpreters that the local provider calls are the pinned ones
    assertThat(output).contains("user=camunda", "Python 3.12.", "v22.");
  }

  @Test
  void shouldUseTheInterpretersAndTheUserOfACustomImage() {
    // given: a custom image, on another base, with the interpreters and its own user. Built
    // locally: with a registry, a registry mirror of Testcontainers does not apply to it.
    final String customImage =
        new ImageFromDockerfile("localhost/camunda-process-test/custom-connectors-it")
            .withDockerfileFromBuilder(
                dockerfile ->
                    dockerfile
                        .from("alpine:3.22")
                        .run("apk add --no-cache python3 nodejs")
                        .user("nobody")
                        .build())
            .get();
    final ManagedScriptsImage image = new ManagedScriptsImage(customImage, null, null);

    // when
    final String output = run(image.build(false));

    // then
    assertThat(output).contains("user=nobody", "Python 3.").containsPattern("v\\d+\\.\\d+");
  }

  @Test
  void shouldFailOnACustomImageWithoutInterpretersAndApk() {
    // given
    final ManagedScriptsImage image = new ManagedScriptsImage("busybox:1.37", null, null);

    // when/then
    assertThatThrownBy(() -> image.build(false).get())
        .hasStackTraceContaining("has no apk to install python3 and node");
  }

  private static String run(final RemoteDockerImage image) {
    try (final GenericContainer<?> container =
        new GenericContainer<>(image)
            .withCreateContainerCmdModifier(
                cmd -> cmd.withEntrypoint("sh", "-c", PRINT_USER_AND_VERSIONS))
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(1)))) {
      container.start();
      return container.getLogs();
    }
  }
}
