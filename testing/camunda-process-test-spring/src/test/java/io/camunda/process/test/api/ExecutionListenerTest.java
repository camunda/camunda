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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.spring.event.CamundaClientClosingSpringEvent;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.runtime.CamundaProcessTestContainerProvider;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.coverage.ProcessCoverage;
import io.camunda.process.test.impl.coverage.ProcessCoverageBuilder;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.TestCaseRunnerProxy;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
  private CamundaProcessTestRuntimeBuilder camundaRuntimeBuilder;

  @Mock(answer = Answers.RETURNS_SELF)
  private ProcessCoverageBuilder processCoverageBuilder;

  @Mock private CamundaProcessTestContainerRuntime camundaContainerRuntime;
  @Mock private ProcessCoverage processCoverage;

  @Mock private CamundaClientProxy camundaClientProxy;
  @Mock private CamundaProcessTestContextProxy camundaProcessTestContextProxy;
  @Mock private TestCaseRunnerProxy testCaseRunnerProxy;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private CamundaProcessTestResultCollector camundaProcessTestResultCollector;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientProperties camundaClientProperties;

  @Captor private ArgumentCaptor<CamundaClient> camundaClientArgumentCaptor;

  @Mock private TestContext testContext;

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private ApplicationContext applicationContext;

  @Mock private JsonMapper jsonMapper;
  @Captor private ArgumentCaptor<CamundaProcessTestContext> camundaProcessTestContextArgumentCaptor;
  @Captor private ArgumentCaptor<TestCaseRunner> testCaseRunnerArgumentCaptor;

  @Captor
  private ArgumentCaptor<CamundaClientCreatedSpringEvent> camundaClientCreatedEventArgumentCaptor;

  @Captor
  private ArgumentCaptor<CamundaClientClosingSpringEvent> camundaClientClosingEventArgumentCaptor;

  @Mock private CamundaProcessTestContainerProvider containerProvider;
  @Mock private CamundaProcessTestContainerProvider anotherContainerProvider;

  @AfterEach
  void resetJudgeConfig() {
    CamundaAssert.setJudgeConfig(null);
    CamundaAssert.setSemanticSimilarityConfig(null);
  }

  @BeforeEach
  void configureMocks() {
    when(camundaRuntimeBuilder.build()).thenReturn(camundaContainerRuntime);

    when(camundaContainerRuntime.getCamundaGrpcApiAddress()).thenReturn(GRPC_API_ADDRESS);
    when(camundaContainerRuntime.getCamundaRestApiAddress()).thenReturn(REST_API_ADDRESS);
    when(camundaContainerRuntime.getCamundaClientBuilderFactory())
        .thenReturn(
            () ->
                CamundaClient.newClientBuilder()
                    .grpcAddress(GRPC_API_ADDRESS)
                    .restAddress(REST_API_ADDRESS));

    when(processCoverageBuilder.build()).thenReturn(processCoverage);
    when(testContext.getApplicationContext()).thenReturn(applicationContext);
    lenient()
        .when(applicationContext.getBean(CamundaClientProxy.class))
        .thenReturn(camundaClientProxy);
    lenient().when(applicationContext.getBean(JsonMapper.class)).thenReturn(null);
    lenient()
        .when(applicationContext.getBean(CamundaProcessTestContextProxy.class))
        .thenReturn(camundaProcessTestContextProxy);
    lenient()
        .when(applicationContext.getBean(TestCaseRunnerProxy.class))
        .thenReturn(testCaseRunnerProxy);
    lenient()
        .when(applicationContext.getBean(CamundaProcessTestRuntimeConfiguration.class))
        .thenReturn(new CamundaProcessTestRuntimeConfiguration());
    lenient()
        .when(applicationContext.getBean(CamundaClientProperties.class))
        .thenReturn(camundaClientProperties);
    lenient().when(applicationContext.getBeansOfType(ChatModelAdapter.class)).thenReturn(Map.of());
    lenient()
        .when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of());
  }

  @Test
  void shouldWireCamundaClient() {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaClientProxy).setDelegate(camundaClientArgumentCaptor.capture());

    final CamundaClient camundaClient = camundaClientArgumentCaptor.getValue();
    assertThat(camundaClient).isNotNull();

    final CamundaClientConfiguration configuration = camundaClient.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);

    verify(applicationContext).publishEvent(camundaClientCreatedEventArgumentCaptor.capture());

    final CamundaClientCreatedSpringEvent createdEvent =
        camundaClientCreatedEventArgumentCaptor.getValue();
    assertThat(createdEvent).isNotNull();
    assertThat(createdEvent.getClient()).isEqualTo(camundaClient);
  }

  @Test
  void shouldWireProcessTestContext() {
    // given
    final URI connectorsRestApiAddress = URI.create("http://my-host:300");
    when(camundaContainerRuntime.getConnectorsRestApiAddress())
        .thenReturn(connectorsRestApiAddress);

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaProcessTestContextProxy)
        .setDelegate(camundaProcessTestContextArgumentCaptor.capture());

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
  void shouldWireTestCaseRunner() {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(testCaseRunnerProxy).setDelegate(testCaseRunnerArgumentCaptor.capture());

    final TestCaseRunner testCaseRunner = testCaseRunnerArgumentCaptor.getValue();
    assertThat(testCaseRunner).isNotNull();
  }

  @Test
  void shouldConfigureJsonMapper() {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    when(applicationContext.getBean(JsonMapper.class)).thenReturn(jsonMapper);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaClientProxy).setDelegate(camundaClientArgumentCaptor.capture());

    final CamundaClient camundaClient = camundaClientArgumentCaptor.getValue();
    assertThat(camundaClient).isNotNull();

    final CamundaClientConfiguration configuration = camundaClient.getConfiguration();
    assertThat(configuration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldStartAndCloseRuntime() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);
    listener.afterTestClass(testContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldCloseCamundaClient() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    listener.afterTestMethod(testContext);

    // then
    verify(applicationContext).publishEvent(camundaClientCreatedEventArgumentCaptor.capture());
    final CamundaClientCreatedSpringEvent createdEvent =
        camundaClientCreatedEventArgumentCaptor.getValue();

    verify(applicationContext).publishEvent(camundaClientClosingEventArgumentCaptor.capture());
    final CamundaClientClosingSpringEvent closedClient =
        camundaClientClosingEventArgumentCaptor.getValue();
    assertThat(createdEvent.getClient()).isEqualTo(closedClient.getClient());

    verify(camundaClientProxy).removeDelegate();
    verify(camundaProcessTestContextProxy).removeDelegate();
  }

  @Test
  void shouldPrintResultIfTestFailed() throws Exception {
    // given
    final StringBuilder outputBuilder = new StringBuilder();
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, outputBuilder::append);

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
            camundaRuntimeBuilder, processCoverageBuilder, outputBuilder::append);

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
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

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

  @Test
  void shouldCollectProcessCoverage() throws Exception {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    final Method method = mock(Method.class);
    when(processCoverageBuilder.build()).thenReturn(processCoverage);
    when(testContext.getTestMethod()).thenReturn(method);
    when(method.getName()).thenReturn("test");

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // CamundaManagementClient will attempt to call purgeCluster() and we need to prevent
    // it from trying to execute real code (the HTTP call will fail).
    setManagementClientDummy(listener);
    listener.afterTestMethod(testContext);
    listener.afterTestClass(testContext);

    // then
    verify(processCoverageBuilder).build();
    verify(processCoverage).collectTestRunCoverage("test");
    verify(processCoverage).reportCoverage();
  }

  @Test
  void shouldConfigureCoverageReport() {
    // given
    final String reportDirectory = "custom/report";
    final List<String> excludedProcesses = List.of("process1", "process2");

    final CamundaProcessTestRuntimeConfiguration configuration =
        new CamundaProcessTestRuntimeConfiguration();
    configuration.getCoverage().setReportDirectory(reportDirectory);
    configuration.getCoverage().setExcludedProcesses(excludedProcesses);

    when(applicationContext.getBean(CamundaProcessTestRuntimeConfiguration.class))
        .thenReturn(configuration);

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(processCoverageBuilder).reportDirectory(reportDirectory);
    verify(processCoverageBuilder).excludeProcessDefinitionIds(excludedProcesses);
  }

  @Test
  void shouldAddCustomContainers() {
    // given
    when(applicationContext.getBeansOfType(CamundaProcessTestContainerProvider.class))
        .thenReturn(Map.of("mock1", containerProvider, "mock2", anotherContainerProvider));

    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);
    listener.beforeTestMethod(testContext);

    // then
    verify(camundaRuntimeBuilder).withContainerProvider(containerProvider);
    verify(camundaRuntimeBuilder).withContainerProvider(anotherContainerProvider);
  }

  @Test
  void shouldNotInitializeJudgeConfigWhenNothingConfigured() {
    // given
    final CamundaProcessTestExecutionListener listener =
        new CamundaProcessTestExecutionListener(
            camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    listener.beforeTestClass(testContext);

    // then
    assertThat(CamundaAssert.getJudgeConfig()).isNull();
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

  @Nested
  class AssertionConfigurationTest {

    @AfterEach
    void resetAssertion() {
      CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
      CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    }

    @Test
    void shouldSetAssertionTimeoutFromConfig() {
      // given
      final Duration assertionTimeout = Duration.ofMinutes(5);
      final Duration assertionInterval = Duration.ofMillis(123);

      final CamundaProcessTestRuntimeConfiguration configuration =
          new CamundaProcessTestRuntimeConfiguration();
      configuration.getAssertion().setTimeout(assertionTimeout);
      configuration.getAssertion().setInterval(assertionInterval);

      when(applicationContext.getBean(CamundaProcessTestRuntimeConfiguration.class))
          .thenReturn(configuration);

      final CamundaProcessTestExecutionListener listener =
          new CamundaProcessTestExecutionListener(
              camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      listener.beforeTestClass(testContext);
      listener.beforeTestMethod(testContext);

      // then
      final CamundaAssertAwaitBehavior awaitBehavior = CamundaAssert.getAwaitBehavior();
      assertThat(awaitBehavior.getAssertionTimeout()).isEqualTo(assertionTimeout);
      assertThat(awaitBehavior.getAssertionInterval()).isEqualTo(assertionInterval);
    }

    @Test
    void shouldNotOverrideAssertionTimeoutIfNotSetInConfig() {
      // given
      final Duration assertionTimeout = Duration.ofMinutes(1);
      final Duration assertionInterval = Duration.ofMillis(50);

      CamundaAssert.setAssertionTimeout(assertionTimeout);
      CamundaAssert.setAssertionInterval(assertionInterval);

      final CamundaProcessTestExecutionListener listener =
          new CamundaProcessTestExecutionListener(
              camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      listener.beforeTestClass(testContext);
      listener.beforeTestMethod(testContext);

      // then
      final CamundaAssertAwaitBehavior awaitBehavior = CamundaAssert.getAwaitBehavior();
      assertThat(awaitBehavior.getAssertionTimeout()).isEqualTo(assertionTimeout);
      assertThat(awaitBehavior.getAssertionInterval()).isEqualTo(assertionInterval);
    }
  }
}
