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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.process.test.impl.configuration.CamundaContainerRuntimeConfiguration;
import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.ZeebeClientProxy;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.spring.client.event.ZeebeClientClosingEvent;
import io.camunda.zeebe.spring.client.event.ZeebeClientCreatedEvent;
import java.net.URI;
import java.util.List;
import java.util.Map;
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

  @Mock(answer = Answers.RETURNS_SELF)
  private CamundaContainerRuntimeBuilder camundaContainerRuntimeBuilder;

  @Mock private CamundaContainerRuntime camundaContainerRuntime;
  @Mock private ZeebeContainer zeebeContainer;
  @Mock private OperateContainer operateContainer;

  @Mock private ZeebeClientProxy zeebeClientProxy;
  @Mock private CamundaProcessTestContextProxy camundaProcessTestContextProxy;

  @Mock private TestContext testContext;

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private ApplicationContext applicationContext;

  @Mock private JsonMapper jsonMapper;

  @Captor private ArgumentCaptor<ZeebeClient> zeebeClientArgumentCaptor;
  @Captor private ArgumentCaptor<CamundaProcessTestContext> camundaProcessTestContextArgumentCaptor;
  @Captor private ArgumentCaptor<ZeebeClientCreatedEvent> zeebeClientCreatedEventArgumentCaptor;
  @Captor private ArgumentCaptor<ZeebeClientClosingEvent> zeebeClientClosingEventArgumentCaptor;

  @BeforeEach
  void configureMocks() {
    when(camundaContainerRuntimeBuilder.build()).thenReturn(camundaContainerRuntime);
    when(camundaContainerRuntime.getZeebeContainer()).thenReturn(zeebeContainer);
    when(zeebeContainer.getGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(zeebeContainer.getRestApiAddress()).thenReturn(REST_API_ADDRESS);

    when(camundaContainerRuntime.getOperateContainer()).thenReturn(operateContainer);
    when(operateContainer.getHost()).thenReturn("my-host");
    when(operateContainer.getRestApiPort()).thenReturn(100);

    when(testContext.getApplicationContext()).thenReturn(applicationContext);
    when(applicationContext.getBean(ZeebeClientProxy.class)).thenReturn(zeebeClientProxy);
    when(applicationContext.getBean(CamundaProcessTestContextProxy.class))
        .thenReturn(camundaProcessTestContextProxy);
    when(applicationContext.getBean(CamundaContainerRuntimeConfiguration.class))
        .thenReturn(new CamundaContainerRuntimeConfiguration());
  }

  @Test
  void shouldWireZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    // when
    listener.beforeTestMethod(testContext);

    // then
    verify(zeebeClientProxy).setZeebeClient(zeebeClientArgumentCaptor.capture());

    final ZeebeClient zeebeClient = zeebeClientArgumentCaptor.getValue();
    assertThat(zeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = zeebeClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);

    verify(applicationContext).publishEvent(zeebeClientCreatedEventArgumentCaptor.capture());

    final ZeebeClientCreatedEvent createdEvent = zeebeClientCreatedEventArgumentCaptor.getValue();
    assertThat(createdEvent).isNotNull();
    assertThat(createdEvent.getClient()).isEqualTo(zeebeClient);
  }

  @Test
  void shouldWireProcessTestContext() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    // when
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaProcessTestContextProxy)
        .setContext(camundaProcessTestContextArgumentCaptor.capture());

    final CamundaProcessTestContext camundaProcessTestContext =
        camundaProcessTestContextArgumentCaptor.getValue();
    assertThat(camundaProcessTestContext).isNotNull();
    assertThat(camundaProcessTestContext.getZeebeGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(camundaProcessTestContext.getZeebeRestAddress()).isEqualTo(REST_API_ADDRESS);

    final ZeebeClient newZeebeClient = camundaProcessTestContext.createClient();
    assertThat(newZeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = newZeebeClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldConfigureJsonMapper() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    when(applicationContext.getBeanNamesForType(JsonMapper.class))
        .thenReturn(new String[] {"zeebeJsonMapper"});

    when(applicationContext.getBean(JsonMapper.class)).thenReturn(jsonMapper);

    // when
    listener.beforeTestMethod(testContext);

    // then
    verify(zeebeClientProxy).setZeebeClient(zeebeClientArgumentCaptor.capture());

    final ZeebeClient zeebeClient = zeebeClientArgumentCaptor.getValue();
    assertThat(zeebeClient).isNotNull();

    final ZeebeClientConfiguration configuration = zeebeClient.getConfiguration();
    assertThat(configuration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldStartAndCloseRuntime() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    // when
    listener.beforeTestMethod(testContext);
    listener.afterTestMethod(testContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldCloseZeebeClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    // when
    listener.beforeTestMethod(testContext);
    listener.afterTestMethod(testContext);

    // then
    verify(applicationContext).publishEvent(zeebeClientCreatedEventArgumentCaptor.capture());
    final ZeebeClientCreatedEvent createdEvent = zeebeClientCreatedEventArgumentCaptor.getValue();

    verify(applicationContext).publishEvent(zeebeClientClosingEventArgumentCaptor.capture());
    final ZeebeClientClosingEvent closedClient = zeebeClientClosingEventArgumentCaptor.getValue();

    assertThat(createdEvent.getClient()).isEqualTo(closedClient.getClient());

    verify(zeebeClientProxy).removeZeebeClient();
    verify(camundaProcessTestContextProxy).removeContext();
  }

  @Test
  void shouldConfigureRuntime() throws Exception {
    // given
    final Map<String, String> zeebeEnvVars =
        Map.ofEntries(entry("env-1", "test-1"), entry("env-2", "test-2"));

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(camundaContainerRuntimeBuilder);

    final CamundaContainerRuntimeConfiguration runtimeConfiguration =
        new CamundaContainerRuntimeConfiguration();
    runtimeConfiguration.setCamundaVersion("8.6.0-custom");
    runtimeConfiguration.setZeebeDockerImageName("custom-zeebe");
    runtimeConfiguration.setZeebeEnvVars(zeebeEnvVars);
    runtimeConfiguration.setZeebeExposedPorts(List.of(100, 200));

    when(applicationContext.getBean(CamundaContainerRuntimeConfiguration.class))
        .thenReturn(runtimeConfiguration);

    // when
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaContainerRuntimeBuilder).withZeebeDockerImageVersion("8.6.0-custom");
    verify(camundaContainerRuntimeBuilder).withOperateDockerImageVersion("8.6.0-custom");
    verify(camundaContainerRuntimeBuilder).withTasklistDockerImageVersion("8.6.0-custom");
    verify(camundaContainerRuntimeBuilder).withZeebeDockerImageName("custom-zeebe");
    verify(camundaContainerRuntimeBuilder).withZeebeEnv(zeebeEnvVars);
    verify(camundaContainerRuntimeBuilder).withZeebeExposedPort(100);
    verify(camundaContainerRuntimeBuilder).withZeebeExposedPort(200);
  }
}
