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

import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.images.builder.ImageFromDockerfile;

class ManagedScriptsImageTest {

  private static final String BASE_IMAGE = "camunda/connectors-bundle:8.11.0";

  private static final String CONNECTORS_USER = "1001:1001";

  @Test
  void shouldAddTheLatestInterpretersByDefault() {
    // when
    final ManagedScriptsImage image = new ManagedScriptsImage(BASE_IMAGE, null, " ");

    // then
    assertThat(image.dockerfile(CONNECTORS_USER))
        .isEqualTo(
            "FROM camunda/connectors-bundle:8.11.0\n"
                + "USER 0\n"
                + "RUN if command -v python3 >/dev/null && command -v node >/dev/null; then :; \\\n"
                + "  elif command -v apk >/dev/null; then apk add --no-cache python3 nodejs; \\\n"
                + "  else echo 'Managed scripts: the Connectors image"
                + " camunda/connectors-bundle:8.11.0 has no apk to install python3 and node. Base"
                + " it on camunda/connectors-bundle, or install python3 and node in it.' >&2;"
                + " exit 1; fi\n"
                + "USER 1001:1001\n");
  }

  @Test
  void shouldAddTheConfiguredVersions() {
    // when
    final ManagedScriptsImage image = new ManagedScriptsImage(BASE_IMAGE, "3.12", "22");

    // then
    assertThat(image.dockerfile(CONNECTORS_USER))
        .contains(
            "RUN if command -v apk >/dev/null; then apk add --no-cache python-3.12 nodejs-22;")
        .contains("has no apk to install the Python and Node.js versions")
        // Pinned versions are installed, even if the image has interpreters already.
        .doesNotContain("command -v python3");
  }

  @Test
  void shouldInstallWhenOnlyOneVersionIsConfigured() {
    // when
    final ManagedScriptsImage image = new ManagedScriptsImage(BASE_IMAGE, null, "22");

    // then
    assertThat(image.dockerfile(CONNECTORS_USER))
        .contains("apk add --no-cache python3 nodejs-22")
        .doesNotContain("command -v python3");
  }

  @Test
  void shouldRunAsTheUserOfACustomImage() {
    // when
    final ManagedScriptsImage image = new ManagedScriptsImage("custom/connectors:1", null, null);

    // then
    assertThat(image.dockerfile("app"))
        .startsWith("FROM custom/connectors:1\nUSER 0\n")
        .endsWith("USER app\n");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void shouldRunAsRootWhenTheCustomImageDoes(final String user) {
    // when
    final ManagedScriptsImage image = new ManagedScriptsImage("custom/connectors:1", null, null);

    // then
    assertThat(image.dockerfile(user)).endsWith("exit 1; fi\n");
  }

  @ParameterizedTest
  @ValueSource(strings = {"3.9", "2.7", "3", "3.12.1", "latest"})
  void shouldRejectAnUnsupportedPythonVersion(final String version) {
    assertThatThrownBy(() -> new ManagedScriptsImage(BASE_IMAGE, version, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Python version '" + version + "'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"18", "22.1", "v22", "lts"})
  void shouldRejectAnUnsupportedNodeVersion(final String version) {
    assertThatThrownBy(() -> new ManagedScriptsImage(BASE_IMAGE, null, version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid Node.js version '" + version + "'");
  }

  @Test
  void shouldNameTheImageAfterItsDockerfile() {
    // given
    final ManagedScriptsImage image = new ManagedScriptsImage(BASE_IMAGE, null, null);

    // then
    assertThat(image.imageName(CONNECTORS_USER))
        .matches(ManagedScriptsImage.IMAGE_NAME + ":[0-9a-f]{12}")
        .isEqualTo(new ManagedScriptsImage(BASE_IMAGE, null, null).imageName(CONNECTORS_USER))
        .isNotEqualTo(image.imageName("app"))
        .isNotEqualTo(new ManagedScriptsImage(BASE_IMAGE, "3.12", null).imageName(CONNECTORS_USER))
        .isNotEqualTo(
            new ManagedScriptsImage("custom/connectors:1", null, null).imageName(CONNECTORS_USER));
  }

  @Test
  void shouldKeepTheBuiltImage() {
    // when
    final ImageFromDockerfile image =
        new ManagedScriptsImage(BASE_IMAGE, null, null).build(CONNECTORS_USER);

    // then
    assertThat(image.isDeleteOnExit()).isFalse();
  }

  @Test
  void shouldBuildLazily() {
    // when: no Docker is needed until the container starts
    final Future<String> image = new ManagedScriptsImage(BASE_IMAGE, null, null).build(true);

    // then
    assertThat(image.isDone()).isFalse();
  }

  @Test
  void shouldEnableTheLocalProvider() {
    assertThat(ManagedScriptsImage.envVars())
        .containsEntry(ManagedScriptsImage.ENV_MANAGED_CODE_ENABLED, "true")
        .containsEntry(ManagedScriptsImage.ENV_MANAGED_CODE_PROVIDER, "local")
        .containsEntry(ManagedScriptsImage.ENV_MANAGED_CODE_LOCAL_DIRECTORY, "/tmp/managed-script");
  }
}
