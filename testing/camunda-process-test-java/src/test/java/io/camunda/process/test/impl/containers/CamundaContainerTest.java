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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.utility.DockerImageName;

class CamundaContainerTest {

  private static final String RDBMS_HISTORY_DEFAULT_HISTORY_TTL =
      "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_DEFAULTHISTORYTTL";
  private static final String RDBMS_HISTORY_MIN_HISTORY_CLEANUP_INTERVAL =
      "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MINHISTORYCLEANUPINTERVAL";
  private static final String RDBMS_HISTORY_MAX_HISTORY_CLEANUP_INTERVAL =
      "CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MAXHISTORYCLEANUPINTERVAL";

  @Test
  void shouldNotConfigureRdbmsHistoryCleanupForH2() {
    // given
    final CamundaContainer container =
        new CamundaContainer(DockerImageName.parse("camunda/camunda:SNAPSHOT"));

    // when
    final Map<String, String> envs = container.getEnvMap();

    // then
    assertThat(envs)
        .doesNotContainKeys(
            RDBMS_HISTORY_DEFAULT_HISTORY_TTL,
            RDBMS_HISTORY_MIN_HISTORY_CLEANUP_INTERVAL,
            RDBMS_HISTORY_MAX_HISTORY_CLEANUP_INTERVAL);
  }

  @Test
  void shouldProvideClusterSecretsAsFilesInFileStore() {
    // given
    final CamundaContainer container =
        new CamundaContainer(DockerImageName.parse("camunda/camunda:SNAPSHOT"));

    // when
    final Map<String, String> secrets = new HashMap<>();
    secrets.put("MY_API_KEY", "test-key");
    secrets.put("OTHER", "other-value");
    container.withClusterSecrets(secrets);

    // then
    assertThat(container.getEnvMap())
        .containsEntry("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH", "/tmp/camunda-cluster-secrets");
    assertThat(container.getCopyToFileContainerPathMap().values())
        .containsExactlyInAnyOrder(
            "/tmp/camunda-cluster-secrets/MY_API_KEY", "/tmp/camunda-cluster-secrets/OTHER");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"../escape", "/tmp/escape", "dir/secret", "dir\\secret", ".hidden", "..", "", " "})
  void shouldRejectClusterSecretNameThatIsNotASinglePathSegment(final String name) {
    // given
    final CamundaContainer container =
        new CamundaContainer(DockerImageName.parse("camunda/camunda:SNAPSHOT"));
    final Map<String, String> secrets = new HashMap<>();
    secrets.put(name, "value");

    // when / then
    assertThatThrownBy(() -> container.withClusterSecrets(secrets))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(name);
    assertThat(container.getCopyToFileContainerPathMap()).isEmpty();
  }

  @Test
  void shouldNotConfigureFileStoreWithoutClusterSecrets() {
    // given
    final CamundaContainer container =
        new CamundaContainer(DockerImageName.parse("camunda/camunda:SNAPSHOT"));

    // when
    container.withClusterSecrets(new HashMap<>());

    // then
    assertThat(container.getEnvMap()).doesNotContainKey("CAMUNDA_SECRETS_STORES_FILE_DEFAULT_PATH");
    assertThat(container.getCopyToFileContainerPathMap()).isEmpty();
  }
}
