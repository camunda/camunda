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

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.configuration.CamundaContainerRuntimeConfiguration;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.ZeebeClientProxy;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.spring.client.event.CamundaClientClosingEvent;
import io.camunda.spring.client.event.CamundaClientCreatedEvent;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.spring.client.event.ZeebeClientClosingEvent;
import io.camunda.zeebe.spring.client.event.ZeebeClientCreatedEvent;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;

@ExtendWith(MockitoExtension.class)
public class ExecutionListenerTest {

  private static final URI GRPC_API_ADDRESS = URI.create("http://my-host:100");
  private static final URI REST_API_ADDRESS = URI.create("http://my-host:200");

  private static final Consumer<String> NOOP = s -> {};

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainerRuntimeBuilder camundaContainerRuntimeBuilder;

  @Mock private CamundaContainerRuntime camundaContainerRuntime;
  @Mock private CamundaContainer camundaContainer;
  @Mock private ConnectorsContainer connectorsContainer;

  @Mock private CamundaClientProxy camundaClientProxy;
  @Mock private ZeebeClientProxy zeebeClientProxy;
  @Mock private CamundaProcessTestContextProxy camundaProcessTestContextProxy;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private CamundaProcessTestResultCollector camundaProcessTestResultCollector;

  @Mock private TestContext testContext;

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private ApplicationContext applicationContext;

  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeClientJsonMapper;

  @Captor private ArgumentCaptor<CamundaClient> camundaClientArgumentCaptor;
  @Captor private ArgumentCaptor<ZeebeClient> zeebeClientArgumentCaptor;
  @Captor private ArgumentCaptor<CamundaProcessTestContext> camundaProcessTestContextArgumentCaptor;
  @Captor private ArgumentCaptor<CamundaClientCreatedEvent> camundaClientCreatedEventArgumentCaptor;
  @Captor private ArgumentCaptor<CamundaClientClosingEvent> camundaClientClosingEventArgumentCaptor;
  @Captor private ArgumentCaptor<ZeebeClientCreatedEvent> zeebeClientCreatedEventArgumentCaptor;
  @Captor private ArgumentCaptor<ZeebeClientClosingEvent> zeebeClientClosingEventArgumentCaptor;

  @BeforeEach
  void configureMocks() {
    when(camundaContainerRuntimeBuilder.build()).thenReturn(camundaContainerRuntime);
    when(camundaContainerRuntime.getCamundaContainer()).thenReturn(camundaContainer);
    when(camundaContainer.getGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(camundaContainer.getRestApiAddress()).thenReturn(REST_API_ADDRESS);

    when(camundaContainerRuntime.getConnectorsContainer()).thenReturn(connectorsContainer);

    when(testContext.getApplicationContext()).thenReturn(applicationContext);
    when(applicationContext.getBean(CamundaClientProxy.class)).thenReturn(camundaClientProxy);
    when(applicationContext.getBean(ZeebeClientProxy.class)).thenReturn(zeebeClientProxy);
    when(applicationContext.getBean(CamundaProcessTestContextProxy.class))
        .thenReturn(camundaProcessTestContextProxy);
    when(applicationContext.getBean(CamundaContainerRuntimeConfiguration.class))
        .thenReturn(new CamundaContainerRuntimeConfiguration());
  }

  @Test
  void shouldWireCamundaClientAndZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaClientProxy).setClient(camundaClientArgumentCaptor.capture());
    verify(zeebeClientProxy).setClient(zeebeClientArgumentCaptor.capture());

    final CamundaClient camundaClient = camundaClientArgumentCaptor.getValue();
    assertThat(camundaClient).isNotNull();

    final ZeebeClient zeebeClient = zeebeClientArgumentCaptor.getValue();
    assertThat(zeebeClient).isNotNull();

    final CamundaClientConfiguration configuration = camundaClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);

    verify(applicationContext).publishEvent(camundaClientCreatedEventArgumentCaptor.capture());
    verify(applicationContext).publishEvent(zeebeClientCreatedEventArgumentCaptor.capture());

    final CamundaClientCreatedEvent createdEvent =
        camundaClientCreatedEventArgumentCaptor.getValue();
    assertThat(createdEvent).isNotNull();
    assertThat(createdEvent.getClient()).isEqualTo(camundaClient);
    final ZeebeClientCreatedEvent zeebeClientCreatedEvent =
        zeebeClientCreatedEventArgumentCaptor.getValue();
    assertThat(zeebeClientCreatedEvent).isNotNull();
    assertThat(zeebeClientCreatedEvent.getClient()).isEqualTo(zeebeClient);
  }

  @Test
  void shouldWireProcessTestContext() throws Exception {
    // given
    final URI connectorsRestApiAddress = URI.create("http://my-host:300");
    when(connectorsContainer.getRestApiAddress()).thenReturn(connectorsRestApiAddress);

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaProcessTestContextProxy)
        .setContext(camundaProcessTestContextArgumentCaptor.capture());

    final CamundaProcessTestContext camundaProcessTestContext =
        camundaProcessTestContextArgumentCaptor.getValue();
    assertThat(camundaProcessTestContext).isNotNull();
    assertThat(camundaProcessTestContext.getCamundaGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(camundaProcessTestContext.getCamundaRestAddress()).isEqualTo(REST_API_ADDRESS);
    assertThat(camundaProcessTestContext.getConnectorsAddress())
        .isEqualTo(connectorsRestApiAddress);

    final CamundaClient newCamundaClient = camundaProcessTestContext.createClient();
    assertThat(newCamundaClient).isNotNull();

    final CamundaClientConfiguration configuration = newCamundaClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldConfigureJsonMapper() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    when(applicationContext.getBeanNamesForType(JsonMapper.class))
        .thenReturn(new String[] {"camundaJsonMapper"});

    when(applicationContext.getBean(JsonMapper.class)).thenReturn(jsonMapper);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaClientProxy).setClient(camundaClientArgumentCaptor.capture());

    final CamundaClient camundaClient = camundaClientArgumentCaptor.getValue();
    assertThat(camundaClient).isNotNull();

    final CamundaClientConfiguration configuration = camundaClient.getConfiguration();
    assertThat(configuration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldConfigureJsonMapperForZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    when(applicationContext.getBeanNamesForType(JsonMapper.class)).thenReturn(new String[] {});
    when(applicationContext.getBeanNamesForType(io.camunda.zeebe.client.api.JsonMapper.class))
        .thenReturn(new String[] {"zeebeJsonMapper"});

    when(applicationContext.getBean(io.camunda.zeebe.client.api.JsonMapper.class))
        .thenReturn(zeebeClientJsonMapper);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(zeebeClientProxy).setClient(zeebeClientArgumentCaptor.capture());

    final ZeebeClient zeebeClient = zeebeClientArgumentCaptor.getValue();
    assertThat(zeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = zeebeClient.getConfiguration();
    assertThat(configuration.getJsonMapper()).isEqualTo(zeebeClientJsonMapper);
  }

  @Test
  void shouldStartAndCloseRuntime() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);
    listener.afterTestClass(testContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldCloseCamundClientAndZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    listener.afterTestMethod(testContext);

    // then
    verify(applicationContext).publishEvent(camundaClientCreatedEventArgumentCaptor.capture());
    final CamundaClientCreatedEvent createdEvent =
        camundaClientCreatedEventArgumentCaptor.getValue();

    verify(applicationContext).publishEvent(zeebeClientCreatedEventArgumentCaptor.capture());
    final ZeebeClientCreatedEvent zeebeClientCreatedEvent =
        zeebeClientCreatedEventArgumentCaptor.getValue();

    verify(applicationContext).publishEvent(camundaClientClosingEventArgumentCaptor.capture());
    final CamundaClientClosingEvent closedClient =
        camundaClientClosingEventArgumentCaptor.getValue();
    assertThat(createdEvent.getClient()).isEqualTo(closedClient.getClient());

    verify(applicationContext).publishEvent(zeebeClientClosingEventArgumentCaptor.capture());
    final ZeebeClientClosingEvent zeebeClientClosingEvent =
        zeebeClientClosingEventArgumentCaptor.getValue();
    assertThat(zeebeClientCreatedEvent.getClient()).isEqualTo(zeebeClientClosingEvent.getClient());

    verify(camundaClientProxy).removeClient();
    verify(zeebeClientProxy).removeClient();
    verify(camundaProcessTestContextProxy).removeContext();
  }

  @Test
  void shouldConfigureRuntime() throws Exception {
    // given
    final Map<String, String> camundaEnvVars =
        Map.ofEntries(entry("env-1", "test-1"), entry("env-2", "test-2"));

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    final CamundaContainerRuntimeConfiguration runtimeConfiguration =
        new CamundaContainerRuntimeConfiguration();
    runtimeConfiguration.setCamundaVersion("8.6.0-custom");
    runtimeConfiguration.setCamundaDockerImageName("custom-camunda");
    runtimeConfiguration.setCamundaEnvVars(camundaEnvVars);
    runtimeConfiguration.setCamundaExposedPorts(List.of(100, 200));

    when(applicationContext.getBean(CamundaContainerRuntimeConfiguration.class))
        .thenReturn(runtimeConfiguration);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaContainerRuntimeBuilder).withCamundaDockerImageVersion("8.6.0-custom");
    verify(camundaContainerRuntimeBuilder).withCamundaDockerImageName("custom-camunda");
    verify(camundaContainerRuntimeBuilder).withCamundaEnv(camundaEnvVars);
    verify(camundaContainerRuntimeBuilder).withCamundaExposedPort(100);
    verify(camundaContainerRuntimeBuilder).withCamundaExposedPort(200);
  }

  @Test
  void shouldConfigureConnectors() throws Exception {
    // given
    final Map<String, String> connectorsEnvVars =
        Map.ofEntries(entry("env-1", "test-1"), entry("env-2", "test-2"));

    final Map<String, String> connectorsSecrets =
        Map.ofEntries(entry("secret-1", "1"), entry("secret-2", "2"));

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    final CamundaContainerRuntimeConfiguration runtimeConfiguration =
        new CamundaContainerRuntimeConfiguration();
    runtimeConfiguration.setConnectorsEnabled(true);
    runtimeConfiguration.setConnectorsDockerImageName("custom-connectors");
    runtimeConfiguration.setConnectorsDockerImageVersion("8.6.0-custom");
    runtimeConfiguration.setConnectorsEnvVars(connectorsEnvVars);
    runtimeConfiguration.setConnectorsSecrets(connectorsSecrets);

    when(applicationContext.getBean(CamundaContainerRuntimeConfiguration.class))
        .thenReturn(runtimeConfiguration);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaContainerRuntimeBuilder).withConnectorsEnabled(true);
    verify(camundaContainerRuntimeBuilder).withConnectorsDockerImageName("custom-connectors");
    verify(camundaContainerRuntimeBuilder).withConnectorsDockerImageVersion("8.6.0-custom");
    verify(camundaContainerRuntimeBuilder).withConnectorsEnv(connectorsEnvVars);
    verify(camundaContainerRuntimeBuilder).withConnectorsSecrets(connectorsSecrets);
  }

  @Test
  void shouldPrintResultIfTestFailed() throws Exception {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaContainerRuntimeBuilder, outputBuilder::append);

    when(camundaProcessTestResultCollector.collect()).thenReturn(new ProcessTestResult());

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    setTestResultCollectorMock(listener);
    when(testContext.getTestException()).thenReturn(new AssertionError("test failure (expected)"));
    listener.afterTestMethod(testContext);

    // then
    assertThat(outputBuilder.toString()).startsWith("Process test results");
    verify(camundaProcessTestResultCollector).collect();
  }

  @Test
  void shouldNotPrintResultIfTestSuccessful() throws Exception {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaContainerRuntimeBuilder, outputBuilder::append);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    setTestResultCollectorMock(listener);
    when(testContext.getTestException()).thenReturn(null);
    listener.afterTestMethod(testContext);

    // then
    assertThat(outputBuilder.toString()).isEmpty();
    verify(camundaProcessTestResultCollector, never()).collect();
  }

  @Test
  void shouldPurgeTheClusterInBetweenTests() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    listener.afterTestMethod(testContext);

    // then
    verify(camundaManagementClient).purgeCluster();
  }

  private void setManagementClientDummy(final CamundaProcessTestExecutionListener listener) {
    try {
      final Field cmcField = listener.getClass().getDeclaredField("camundaManagementClient");
      cmcField.setAccessible(true);
      cmcField.set(listener, camundaManagementClient);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private void setTestResultCollectorMock(final CamundaProcessTestExecutionListener listener) {
    try {
      final Field cmcField = listener.getClass().getDeclaredField("processTestResultCollector");
      cmcField.setAccessible(true);
      cmcField.set(listener, camundaProcessTestResultCollector);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
