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

import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_DEFAULTHISTORYTTL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MAXHISTORYCLEANUPINTERVAL;
import static io.camunda.process.test.impl.runtime.ContainerRuntimeEnvs.CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MINHISTORYCLEANUPINTERVAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

class CamundaContainerTest {

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
            CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_DEFAULTHISTORYTTL,
            CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MINHISTORYCLEANUPINTERVAL,
            CAMUNDA_ENV_DATA_SECONDARYSTORAGE_RDBMS_HISTORY_MAXHISTORYCLEANUPINTERVAL);
  }
}
