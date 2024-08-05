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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.TestInstances;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JunitExtensionTest {

  private static final URI GRPC_API_ADDRESS = URI.create("http://my-host:100");
  private static final URI REST_API_ADDRESS = URI.create("http://my-host:200");

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainerRuntimeBuilder camundaContainerRuntimeBuilder;

  @Mock private CamundaContainerRuntime camundaContainerRuntime;
  @Mock private ZeebeContainer zeebeContainer;
  @Mock private OperateContainer operateContainer;

  @Mock private ExtensionContext extensionContext;
  @Mock private TestInstances testInstances;
  @Mock private Store store;

  // to be injected
  private ZeebeClient client;
  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaContainerRuntimeBuilder.build()).thenReturn(camundaContainerRuntime);
    when(camundaContainerRuntime.getZeebeContainer()).thenReturn(zeebeContainer);
    when(zeebeContainer.getGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(zeebeContainer.getRestApiAddress()).thenReturn(REST_API_ADDRESS);

    when(camundaContainerRuntime.getOperateContainer()).thenReturn(operateContainer);
    when(operateContainer.getHost()).thenReturn("my-host");
    when(operateContainer.getRestApiPort()).thenReturn(100);

    when(extensionContext.getRequiredTestInstances()).thenReturn(testInstances);
    when(testInstances.getAllInstances()).thenReturn(Collections.singletonList(this));
    when(extensionContext.getStore(any())).thenReturn(store);
  }

  @Test
  void shouldInjectZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);

    // then
    assertThat(client).isNotNull();

    final ZeebeClientConfiguration configuration = client.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldInjectContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);

    // then
    assertThat(camundaProcessTestContext).isNotNull();
    assertThat(camundaProcessTestContext.getZeebeGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(camundaProcessTestContext.getZeebeRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldCreateZeebeClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);

    // then
    final ZeebeClient newZeebeClient = camundaProcessTestContext.createClient();
    assertThat(newZeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = newZeebeClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldCreateCustomZeebeClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);

    // then
    final ZeebeClient newZeebeClient =
        camundaProcessTestContext.createClient(builder -> builder.defaultJobWorkerName("test"));
    assertThat(newZeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = newZeebeClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
    assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("test");
  }

  @Test
  void shouldStartAndCloseRuntime() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);
    extension.afterEach(extensionContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldStoreRuntimeAndContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder);

    // when
    extension.beforeEach(extensionContext);

    // then
    verify(store).put(CamundaProcessTestExtension.STORE_KEY_RUNTIME, camundaContainerRuntime);
    verify(store).put(CamundaProcessTestExtension.STORE_KEY_CONTEXT, camundaProcessTestContext);
  }

  @Test
  void shouldConfigureRuntime() throws Exception {
    // given
    final String camundaVersion = "camunda-version";
    final String zeebeDockerImageName = "zeebe-docker-image-name";
    final Map<String, String> zeebeEnvVars = new HashMap<>();
    zeebeEnvVars.put("env-1", "test-1");
    zeebeEnvVars.put("env-2", "test-2");

    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder)
            .withCamundaVersion(camundaVersion)
            .withZeebeDockerImageName(zeebeDockerImageName)
            .withZeebeEnv(zeebeEnvVars)
            .withZeebeEnv("env-3", "test-3")
            .withZeebeExposedPort(100)
            .withZeebeExposedPort(200);

    // when
    extension.beforeEach(extensionContext);

    // then
    verify(camundaContainerRuntimeBuilder).withZeebeDockerImageVersion(camundaVersion);
    verify(camundaContainerRuntimeBuilder).withOperateDockerImageVersion(camundaVersion);
    verify(camundaContainerRuntimeBuilder).withTasklistDockerImageVersion(camundaVersion);

    verify(camundaContainerRuntimeBuilder).withZeebeDockerImageName(zeebeDockerImageName);

    verify(camundaContainerRuntimeBuilder).withZeebeEnv(zeebeEnvVars);
    verify(camundaContainerRuntimeBuilder).withZeebeEnv("env-3", "test-3");

    verify(camundaContainerRuntimeBuilder).withZeebeExposedPort(100);
    verify(camundaContainerRuntimeBuilder).withZeebeExposedPort(200);
  }
}
