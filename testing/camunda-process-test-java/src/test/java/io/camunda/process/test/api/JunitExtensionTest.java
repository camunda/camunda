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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.platform.commons.util.ExceptionUtils;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JunitExtensionTest {

  private static final URI GRPC_API_ADDRESS = URI.create("http://my-host:100");
  private static final URI REST_API_ADDRESS = URI.create("http://my-host:200");

  private static final Consumer<String> NOOP = s -> {};

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainerRuntimeBuilder camundaContainerRuntimeBuilder;

  @Mock private CamundaContainerRuntime camundaContainerRuntime;
  @Mock private CamundaContainer camundaContainer;
  @Mock private ConnectorsContainer connectorsContainer;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private CamundaProcessTestResultCollector camundaProcessTestResultCollector;

  @Mock private ExtensionContext extensionContext;
  @Mock private TestInstances testInstances;
  @Mock private Store store;

  // to be injected
  private CamundaClient client;
  private ZeebeClient zeebeClient;
  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaContainerRuntimeBuilder.build()).thenReturn(camundaContainerRuntime);
    when(camundaContainerRuntime.getCamundaContainer()).thenReturn(camundaContainer);
    when(camundaContainer.getGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(camundaContainer.getRestApiAddress()).thenReturn(REST_API_ADDRESS);

    when(camundaContainerRuntime.getConnectorsContainer()).thenReturn(connectorsContainer);

    when(extensionContext.getRequiredTestInstances()).thenReturn(testInstances);
    when(testInstances.getAllInstances()).thenReturn(Collections.singletonList(this));
    when(extensionContext.getStore(any())).thenReturn(store);
  }

  @Test
  void shouldInjectCamundaClientAndZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    assertThat(client).isNotNull();
    assertThat(zeebeClient).isNotNull();

    final CamundaClientConfiguration configuration = client.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
    final ZeebeClientConfiguration zeebeClientConfiguration = zeebeClient.getConfiguration();
    assertThat(zeebeClientConfiguration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(zeebeClientConfiguration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldInjectContext() throws Exception {
    // given
    final URI connectorsRestApiAddress = URI.create("http://my-host:300");
    when(connectorsContainer.getRestApiAddress()).thenReturn(connectorsRestApiAddress);

    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    assertThat(camundaProcessTestContext).isNotNull();
    assertThat(camundaProcessTestContext.getCamundaGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(camundaProcessTestContext.getCamundaRestAddress()).isEqualTo(REST_API_ADDRESS);
    assertThat(camundaProcessTestContext.getConnectorsAddress())
        .isEqualTo(connectorsRestApiAddress);
  }

  @Test
  void shouldCreateCamundaClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    final CamundaClient newCamundaClient = camundaProcessTestContext.createClient();
    assertThat(newCamundaClient).isNotNull();

    final CamundaClientConfiguration configuration = newCamundaClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldCreateZeebeClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    final ZeebeClient newZeebeClient = camundaProcessTestContext.createZeebeClient();
    assertThat(newZeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = newZeebeClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldCreateCustomCamundaClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    final CamundaClient newCamundaClient =
        camundaProcessTestContext.createClient(builder -> builder.defaultJobWorkerName("test"));
    assertThat(newCamundaClient).isNotNull();

    final CamundaClientConfiguration configuration = newCamundaClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
    assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("test");
  }

  @Test
  void shouldCreateCustomZeebeClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    final ZeebeClient newZeebeClient =
        camundaProcessTestContext.createZeebeClient(
            builder -> builder.defaultJobWorkerName("test"));
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
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);
    extension.afterAll(extensionContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldStoreRuntimeAndContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    verify(store).put(CamundaProcessTestExtension.STORE_KEY_RUNTIME, camundaContainerRuntime);
    verify(store).put(CamundaProcessTestExtension.STORE_KEY_CONTEXT, camundaProcessTestContext);
  }

  @Test
  void shouldConfigureRuntime() throws Exception {
    // given
    final String camundaVersion = "camunda-version";
    final String camundaDockerImageName = "camunda-docker-image-name";
    final Map<String, String> camundaEnvVars = new HashMap<>();
    camundaEnvVars.put("env-1", "test-1");
    camundaEnvVars.put("env-2", "test-2");

    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP)
            .withCamundaVersion(camundaVersion)
            .withCamundaDockerImageName(camundaDockerImageName)
            .withCamundaEnv(camundaEnvVars)
            .withCamundaEnv("env-3", "test-3")
            .withCamundaExposedPort(100)
            .withCamundaExposedPort(200);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    verify(camundaContainerRuntimeBuilder).withCamundaDockerImageVersion(camundaVersion);
    verify(camundaContainerRuntimeBuilder).withConnectorsDockerImageVersion(camundaVersion);

    verify(camundaContainerRuntimeBuilder).withCamundaDockerImageName(camundaDockerImageName);

    verify(camundaContainerRuntimeBuilder).withCamundaEnv(camundaEnvVars);
    verify(camundaContainerRuntimeBuilder).withCamundaEnv("env-3", "test-3");

    verify(camundaContainerRuntimeBuilder).withCamundaExposedPort(100);
    verify(camundaContainerRuntimeBuilder).withCamundaExposedPort(200);
  }

  @Test
  void shouldConfigureConnectors() throws Exception {
    // given
    final String connectorsVersion = "connector-version";
    final String connectorsDockerImageName = "connectors-docker-image-name";
    final Map<String, String> connectorsEnvVars = new HashMap<>();
    connectorsEnvVars.put("env-1", "test-1");
    connectorsEnvVars.put("env-2", "test-2");

    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP)
            .withConnectorsEnabled(true)
            .withConnectorsDockerImageName(connectorsDockerImageName)
            .withConnectorsDockerImageVersion(connectorsVersion)
            .withConnectorsEnv(connectorsEnvVars)
            .withConnectorsEnv("env-3", "test-3")
            .withConnectorsSecret("secret-1", "1")
            .withConnectorsSecret("secret-2", "2");

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    verify(camundaContainerRuntimeBuilder).withConnectorsEnabled(true);

    verify(camundaContainerRuntimeBuilder).withConnectorsDockerImageName(connectorsDockerImageName);
    verify(camundaContainerRuntimeBuilder).withConnectorsDockerImageVersion(connectorsVersion);

    verify(camundaContainerRuntimeBuilder).withConnectorsEnv(connectorsEnvVars);
    verify(camundaContainerRuntimeBuilder).withConnectorsEnv("env-3", "test-3");

    verify(camundaContainerRuntimeBuilder).withConnectorsSecret("secret-1", "1");
    verify(camundaContainerRuntimeBuilder).withConnectorsSecret("secret-2", "2");
  }

  @Test
  void shouldPrintResultIfTestFailed() throws Exception {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, outputBuilder::append);

    when(camundaProcessTestResultCollector.collect()).thenReturn(new ProcessTestResult());

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    when(extensionContext.getExecutionException())
        .thenReturn(Optional.of(new AssertionError("test failure (expected)")));

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(extension);
    setTestResultCollectorMock(extension);
    extension.afterEach(extensionContext);

    // then
    assertThat(outputBuilder.toString()).startsWith("Process test results");
    verify(camundaProcessTestResultCollector).collect();
  }

  @Test
  void shouldNotPrintResultIfTestSuccessful() throws Exception {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, outputBuilder::append);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    when(extensionContext.getExecutionException()).thenReturn(Optional.empty());

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(extension);
    setTestResultCollectorMock(extension);
    extension.afterEach(extensionContext);

    // then
    assertThat(outputBuilder.toString()).isEmpty();
    verify(camundaProcessTestResultCollector, never()).collect();
  }

  @Test
  void shouldPurgeTheClusterInBetweenTests() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaContainerRuntimeBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(extension);
    extension.afterEach(extensionContext);

    // then
    verify(camundaManagementClient).purgeCluster();
  }

  private void setManagementClientDummy(final CamundaProcessTestExtension extension) {
    try {
      final Field cmcField = extension.getClass().getDeclaredField("camundaManagementClient");
      cmcField.setAccessible(true);
      cmcField.set(extension, camundaManagementClient);
    } catch (final Throwable t) {
      ExceptionUtils.throwAsUncheckedException(t);
    }
  }

  private void setTestResultCollectorMock(final CamundaProcessTestExtension extension) {
    try {
      final Field cmcField = extension.getClass().getDeclaredField("processTestResultCollector");
      cmcField.setAccessible(true);
      cmcField.set(extension, camundaProcessTestResultCollector);
    } catch (final Throwable t) {
      ExceptionUtils.throwAsUncheckedException(t);
    }
  }
}
