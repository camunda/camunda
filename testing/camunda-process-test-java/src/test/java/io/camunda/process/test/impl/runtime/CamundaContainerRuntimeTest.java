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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.containers.ContainerFactory;
import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.containers.TasklistContainer;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@ExtendWith(MockitoExtension.class)
public class CamundaContainerRuntimeTest {

  private static final Map<String, String> ENV_VARS;
  private static final String ADDITIONAL_ENV_VAR_KEY = "env-3";
  private static final String ADDITIONAL_ENV_VAR_VALUE = "test-3";
  private static final Map<String, String> EXPECTED_ENV_VARS;

  static {
    ENV_VARS = new HashMap<>();
    ENV_VARS.put("env-1", "test-1");
    ENV_VARS.put("env-2", "test-2");

    EXPECTED_ENV_VARS = new HashMap<>(ENV_VARS);
    EXPECTED_ENV_VARS.put(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE);
  }

  @Mock private ContainerFactory containerFactory;

  @Mock(answer = Answers.RETURNS_SELF)
  private ElasticsearchContainer elasticsearchContainer;

  @Mock(answer = Answers.RETURNS_SELF)
  private ZeebeContainer zeebeContainer;

  @Mock(answer = Answers.RETURNS_SELF)
  private OperateContainer operateContainer;

  @Mock(answer = Answers.RETURNS_SELF)
  private TasklistContainer tasklistContainer;

  @BeforeEach
  void configureMocks() {
    when(containerFactory.createElasticsearchContainer(any(), any()))
        .thenReturn(elasticsearchContainer);
    when(containerFactory.createZeebeContainer(any(), any())).thenReturn(zeebeContainer);
    when(containerFactory.createOperateContainer(any(), any())).thenReturn(operateContainer);
    when(containerFactory.createTasklistContainer(any(), any())).thenReturn(tasklistContainer);
  }

  @Test
  void shouldCreateContainers() {
    // given/when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder().withContainerFactory(containerFactory).build();

    // then
    assertThat(runtime).isNotNull();
    assertThat(runtime.getElasticsearchContainer()).isEqualTo(elasticsearchContainer);
    assertThat(runtime.getZeebeContainer()).isEqualTo(zeebeContainer);
    assertThat(runtime.getOperateContainer()).isEqualTo(operateContainer);
    assertThat(runtime.getTasklistContainer()).isEqualTo(tasklistContainer);

    verify(elasticsearchContainer, never()).start();
    verify(zeebeContainer, never()).start();
    verify(operateContainer, never()).start();
    verify(tasklistContainer, never()).start();
  }

  @Test
  void shouldStartAndStopContainers() throws Exception {
    // given
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder().withContainerFactory(containerFactory).build();

    // when
    runtime.start();

    // then
    verify(elasticsearchContainer).start();
    verify(zeebeContainer).start();
    verify(operateContainer).start();
    verify(tasklistContainer).start();

    // and when
    runtime.close();

    // then
    verify(elasticsearchContainer).stop();
    verify(zeebeContainer).stop();
    verify(operateContainer).stop();
    verify(tasklistContainer).stop();
  }

  @Test
  void shouldCreateWithDefaults() {
    // given/when
    CamundaContainerRuntime.newBuilder().withContainerFactory(containerFactory).build();

    // then
    verify(containerFactory)
        .createElasticsearchContainer(
            ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_VERSION);
    verify(containerFactory)
        .createZeebeContainer(
            ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_VERSION);
    verify(containerFactory)
        .createOperateContainer(
            ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_VERSION);
    verify(containerFactory)
        .createTasklistContainer(
            ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_NAME,
            ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_VERSION);
  }

  @Test
  void shouldConfigureZeebeContainer() {
    // given
    final String dockerImageName = "custom-zeebe";
    final String dockerImageVersion = "8.6.0-custom";

    // when
    CamundaContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withZeebeDockerImageName(dockerImageName)
        .withZeebeDockerImageVersion(dockerImageVersion)
        .withZeebeEnv(ENV_VARS)
        .withZeebeEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withZeebeExposedPort(100)
        .withZeebeExposedPort(200)
        .withZeebeLogger("custom-logger")
        .build();

    // then
    verify(containerFactory).createZeebeContainer(dockerImageName, dockerImageVersion);
    verify(zeebeContainer).withEnv(EXPECTED_ENV_VARS);
    verify(zeebeContainer).addExposedPort(100);
    verify(zeebeContainer).addExposedPort(200);
    verify(zeebeContainer).withLogConsumer(any());
  }

  @Test
  void shouldConfigureElasticsearchContainer() {
    // given
    final String dockerImageName = "custom-elasticsearch";
    final String dockerImageVersion = "8.13.0-custom";

    // when
    CamundaContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withElasticsearchDockerImageName(dockerImageName)
        .withElasticsearchDockerImageVersion(dockerImageVersion)
        .withElasticsearchEnv(ENV_VARS)
        .withElasticsearchEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withElasticsearchExposedPort(100)
        .withElasticsearchExposedPort(200)
        .withElasticsearchLogger("custom-logger")
        .build();

    // then
    verify(containerFactory).createElasticsearchContainer(dockerImageName, dockerImageVersion);
    verify(elasticsearchContainer).withEnv(EXPECTED_ENV_VARS);
    verify(elasticsearchContainer).addExposedPort(100);
    verify(elasticsearchContainer).addExposedPort(200);
    verify(elasticsearchContainer).withLogConsumer(any());
  }

  @Test
  void shouldConfigureOperateContainer() {
    // given
    final String dockerImageVersion = "8.6.0-custom";

    // when
    CamundaContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withOperateDockerImageVersion(dockerImageVersion)
        .withOperateEnv(ENV_VARS)
        .withOperateEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withOperateExposedPort(100)
        .withOperateExposedPort(200)
        .withOperateLogger("custom-logger")
        .build();

    // then
    verify(containerFactory)
        .createOperateContainer(
            ContainerRuntimeDefaults.OPERATE_DOCKER_IMAGE_NAME, dockerImageVersion);
    verify(operateContainer).withEnv(EXPECTED_ENV_VARS);
    verify(operateContainer).addExposedPort(100);
    verify(operateContainer).addExposedPort(200);
    verify(operateContainer).withLogConsumer(any());
  }

  @Test
  void shouldConfigureTasklistContainer() {
    // given
    final String dockerImageVersion = "8.6.0-custom";

    // when
    CamundaContainerRuntime.newBuilder()
        .withContainerFactory(containerFactory)
        .withTasklistDockerImageVersion(dockerImageVersion)
        .withTasklistEnv(ENV_VARS)
        .withTasklistEnv(ADDITIONAL_ENV_VAR_KEY, ADDITIONAL_ENV_VAR_VALUE)
        .withTasklistExposedPort(100)
        .withTasklistExposedPort(200)
        .withTasklistLogger("custom-logger")
        .build();

    // then
    verify(containerFactory)
        .createTasklistContainer(
            ContainerRuntimeDefaults.TASKLIST_DOCKER_IMAGE_NAME, dockerImageVersion);
    verify(tasklistContainer).withEnv(EXPECTED_ENV_VARS);
    verify(tasklistContainer).addExposedPort(100);
    verify(tasklistContainer).addExposedPort(200);
    verify(tasklistContainer).withLogConsumer(any());
  }
}
