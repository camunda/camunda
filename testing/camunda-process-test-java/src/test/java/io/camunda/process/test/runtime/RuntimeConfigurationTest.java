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
package io.camunda.process.test.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.containers.TasklistContainer;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.ContainerRuntimeDefaults;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class RuntimeConfigurationTest {

  private static final Map<String, String> ENV_VARS;

  static {
    ENV_VARS = new HashMap<>();
    ENV_VARS.put("env-1", "test-1");
    ENV_VARS.put("env-2", "test-2");
  }

  @Test
  void shouldUseDefaults() {
    // given/when
    final CamundaContainerRuntime runtime = CamundaContainerRuntime.newDefaultRuntime();

    // then
    assertThat(runtime.getZeebeContainer().getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_NAME
                + ":"
                + ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_VERSION);
    assertThat(runtime.getElasticsearchContainer().getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_NAME
                + ":"
                + ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_VERSION);
    assertThat(runtime.getOperateContainer().getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_NAME
                + ":"
                + ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_VERSION);
    assertThat(runtime.getTasklistContainer().getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_NAME
                + ":"
                + ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_VERSION);
  }

  @Test
  void shouldConfigureZeebeContainer() {
    // given
    final String zeebeDockerImageName =
        "ghcr.io/camunda-community-hub/zeebe-with-hazelcast-exporter";
    final String zeebeDockerImageVersion = "8.5.1";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withZeebeDockerImageName(zeebeDockerImageName)
            .withZeebeDockerImageVersion(zeebeDockerImageVersion)
            .withZeebeEnv(ENV_VARS)
            .withZeebeEnv("zeebe-env", "test")
            .withZeebeExposedPort(5701)
            .withZeebeLogger("zeebe-test")
            .build();

    // then
    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();

    assertThat(zeebeContainer.getDockerImageName())
        .isEqualTo(zeebeDockerImageName + ":" + zeebeDockerImageVersion);
    assertThat(zeebeContainer.getEnvMap())
        .containsEntry("zeebe-env", "test")
        .containsAllEntriesOf(ENV_VARS);
    assertThat(zeebeContainer.getExposedPorts()).contains(5701);
  }

  @Test
  void shouldConfigureElasticsearchDockerImage() {
    // given
    final String elasticsearchDockerImageName = "docker.elastic.co/elasticsearch/elasticsearch";
    final String elasticsearchDockerImageVersion = "8.14.0";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withElasticsearchDockerImageName(elasticsearchDockerImageName)
            .withElasticsearchDockerImageVersion(elasticsearchDockerImageVersion)
            .withElasticsearchEnv(ENV_VARS)
            .withElasticsearchEnv("es-env", "test")
            .withElasticsearchExposedPort(9300)
            .withElasticsearchLogger("es-test")
            .build();

    // then
    final ElasticsearchContainer elasticsearchContainer = runtime.getElasticsearchContainer();

    assertThat(elasticsearchContainer.getDockerImageName())
        .isEqualTo(elasticsearchDockerImageName + ":" + elasticsearchDockerImageVersion);
    assertThat(elasticsearchContainer.getEnvMap())
        .containsEntry("es-env", "test")
        .containsAllEntriesOf(ENV_VARS);
    assertThat(elasticsearchContainer.getExposedPorts()).contains(9300);
  }

  @Test
  void shouldConfigureOperateContainer() {
    // given
    final String operateDockerImageVersion = "8.5.1";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withOperateDockerImageVersion(operateDockerImageVersion)
            .withOperateEnv(ENV_VARS)
            .withOperateEnv("operate-env", "test")
            .withOperateExposedPort(9600)
            .withOperateLogger("operate-test")
            .build();

    // then
    final OperateContainer operateContainer = runtime.getOperateContainer();

    assertThat(operateContainer.getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_NAME + ":" + operateDockerImageVersion);
    assertThat(operateContainer.getEnvMap())
        .containsEntry("operate-env", "test")
        .containsAllEntriesOf(ENV_VARS);
    assertThat(operateContainer.getExposedPorts()).contains(9600);
  }

  @Test
  void shouldConfigureTasklistContainer() {
    // given
    final String tasklistDockerImageVersion = "8.5.1";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withTasklistDockerImageVersion(tasklistDockerImageVersion)
            .withTasklistEnv(ENV_VARS)
            .withTasklistEnv("tasklist-env", "test")
            .withTasklistExposedPort(9600)
            .withTasklistLogger("tasklist-test")
            .build();

    // then
    final TasklistContainer tasklistContainer = runtime.getTasklistContainer();

    assertThat(tasklistContainer.getDockerImageName())
        .isEqualTo(
            ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_NAME + ":" + tasklistDockerImageVersion);
    assertThat(tasklistContainer.getEnvMap())
        .containsEntry("tasklist-env", "test")
        .containsAllEntriesOf(ENV_VARS);
    assertThat(tasklistContainer.getExposedPorts()).contains(9600);
  }
}
