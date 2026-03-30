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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.runtime.CamundaProcessTestContainerProvider;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.coverage.ProcessCoverage;
import io.camunda.process.test.impl.coverage.ProcessCoverageBuilder;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.process.test.utils.FakeChatModelAdapterProvider;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
  private CamundaProcessTestRuntimeBuilder camundaRuntimeBuilder;

  @Mock(answer = Answers.RETURNS_SELF)
  private ProcessCoverageBuilder processCoverageBuilder;

  @Mock private ProcessCoverage processCoverage;
  @Mock private CamundaProcessTestContainerRuntime camundaContainerRuntime;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private CamundaProcessTestResultCollector camundaProcessTestResultCollector;
  @Mock private CamundaProcessTestContainerProvider containerProvider;

  @Mock private ExtensionContext extensionContext;
  @Mock private TestInstances testInstances;
  @Mock private Store store;

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext camundaProcessTestContext;
  private TestCaseRunner testCaseRunner;

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

    when(extensionContext.getTestClass()).thenReturn(Optional.of(MainProcessTest.class));
    when(extensionContext.getRequiredTestInstances()).thenReturn(testInstances);
    when(testInstances.getAllInstances()).thenReturn(Collections.singletonList(this));
    when(extensionContext.getStore(any())).thenReturn(store);
    when(processCoverageBuilder.build()).thenReturn(processCoverage);
  }

  @Test
  void shouldInjectCamundaClient() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    assertThat(client).isNotNull();

    final CamundaClientConfiguration configuration = client.getConfiguration();
    assertThat(configuration.getGrpcAddress()).isEqualTo(GRPC_API_ADDRESS);
    assertThat(configuration.getRestAddress()).isEqualTo(REST_API_ADDRESS);
  }

  @Test
  void shouldInjectContext() throws Exception {
    // given
    final URI connectorsRestApiAddress = URI.create("http://my-host:300");
    when(camundaContainerRuntime.getConnectorsRestApiAddress())
        .thenReturn(connectorsRestApiAddress);

    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

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
  void shouldInjectTestCaseRunner() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    assertThat(testCaseRunner).isNotNull();
  }

  @Test
  void shouldCreateCamundaClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

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
  void shouldCreateCustomCamundaClientFromContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

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
  void shouldStartAndCloseRuntime() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);
    extension.afterAll(extensionContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime).close();
  }

  @Test
  void shouldNotStartAndCloseRuntimeInNestedContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

    final ExtensionContext nestedContext = mock(ExtensionContext.class);
    when(nestedContext.getTestClass())
        .thenReturn(Optional.of(MainProcessTest.NestedProcessTest.class));

    // when
    extension.beforeAll(extensionContext);
    extension.beforeAll(nestedContext);
    extension.beforeEach(extensionContext);
    extension.afterAll(nestedContext);

    // then
    verify(camundaContainerRuntime).start();
    verify(camundaContainerRuntime, times(0)).close();
  }

  @Test
  void shouldStoreRuntimeAndContext() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

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
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP)
            .withCamundaDockerImageVersion(camundaVersion)
            .withCamundaDockerImageName(camundaDockerImageName)
            .withConnectorsDockerImageVersion(camundaVersion)
            .withCamundaEnv(camundaEnvVars)
            .withCamundaEnv("env-3", "test-3")
            .withCamundaExposedPort(100)
            .withCamundaExposedPort(200)
            .withCoverageReportDirectory("custom/reports")
            .withCoverageExcludedProcesses("process-1", "process-2");

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    verify(camundaRuntimeBuilder).withCamundaDockerImageVersion(camundaVersion);
    verify(camundaRuntimeBuilder).withConnectorsDockerImageVersion(camundaVersion);

    verify(camundaRuntimeBuilder).withCamundaDockerImageName(camundaDockerImageName);

    verify(camundaRuntimeBuilder).withCamundaEnv(camundaEnvVars);
    verify(camundaRuntimeBuilder).withCamundaEnv("env-3", "test-3");

    verify(camundaRuntimeBuilder).withCamundaExposedPort(100);
    verify(camundaRuntimeBuilder).withCamundaExposedPort(200);

    verify(camundaRuntimeBuilder).withCoverageReportDirectory("custom/reports");
    verify(camundaRuntimeBuilder)
        .withCoverageExcludedProcesses(Arrays.asList("process-1", "process-2"));
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
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP)
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
    verify(camundaRuntimeBuilder).withConnectorsEnabled(true);

    verify(camundaRuntimeBuilder).withConnectorsDockerImageName(connectorsDockerImageName);
    verify(camundaRuntimeBuilder).withConnectorsDockerImageVersion(connectorsVersion);

    verify(camundaRuntimeBuilder).withConnectorsEnv(connectorsEnvVars);
    verify(camundaRuntimeBuilder).withConnectorsEnv("env-3", "test-3");

    verify(camundaRuntimeBuilder).withConnectorsSecret("secret-1", "1");
    verify(camundaRuntimeBuilder).withConnectorsSecret("secret-2", "2");
  }

  @Test
  void shouldAddCustomContainerProviders() throws Exception {
    // given
    final CamundaProcessTestExtension extension =
        new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP)
            .withContainerProvider(containerProvider)
            .withContainerProvidersServiceLoaderEnabled(true);

    // when
    extension.beforeAll(extensionContext);
    extension.beforeEach(extensionContext);

    // then
    verify(camundaRuntimeBuilder).withContainerProvider(containerProvider);
    verify(camundaRuntimeBuilder).withContainerProvidersServiceLoaderEnabled(true);
  }

  @CamundaProcessTest
  private static final class MainProcessTest {
    static class NestedProcessTest {}
  }

  @Nested
  class AfterEachTests {

    @Test
    void shouldPrintResultIfTestFailed() throws Exception {
      // given
      final StringBuilder outputBuilder = new StringBuilder();
      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(
              camundaRuntimeBuilder, processCoverageBuilder, outputBuilder::append);

      when(camundaProcessTestResultCollector.collect()).thenReturn(new ProcessTestResult());

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      setManagementClientDummy(extension);
      setTestResultCollectorMock(extension);

      when(extensionContext.getExecutionException())
          .thenReturn(Optional.of(new AssertionError("test failure (expected)")));

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
          new CamundaProcessTestExtension(
              camundaRuntimeBuilder, processCoverageBuilder, outputBuilder::append);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      setManagementClientDummy(extension);
      setTestResultCollectorMock(extension);

      when(extensionContext.getExecutionException()).thenReturn(Optional.empty());

      extension.afterEach(extensionContext);

      // then
      assertThat(outputBuilder.toString()).isEmpty();
      verify(camundaProcessTestResultCollector, never()).collect();
    }

    @Test
    void shouldPurgeCluster() throws Exception {
      // given
      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      setManagementClientDummy(extension);

      extension.afterEach(extensionContext);

      // then
      verify(camundaManagementClient).purgeCluster();
    }

    @Test
    void shouldResetClock() throws Exception {
      // given
      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      setManagementClientDummy(extension);

      extension.afterEach(extensionContext);

      // then
      verify(camundaManagementClient).resetTime();
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

  @Nested
  class AssertionConfigurationTest {

    @AfterEach
    void resetAssertion() {
      CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
      CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    }

    @Test
    void shouldSetAssertionTimeoutFromConfig() throws Exception {
      // given
      final Duration assertionTimeout = Duration.ofMinutes(5);
      final Duration assertionInterval = Duration.ofMillis(123);

      when(camundaRuntimeBuilder.getAssertionTimeout()).thenReturn(Optional.of(assertionTimeout));
      when(camundaRuntimeBuilder.getAssertionInterval()).thenReturn(Optional.of(assertionInterval));

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      final CamundaAssertAwaitBehavior awaitBehavior = CamundaAssert.getAwaitBehavior();
      assertThat(awaitBehavior.getAssertionTimeout()).isEqualTo(assertionTimeout);
      assertThat(awaitBehavior.getAssertionInterval()).isEqualTo(assertionInterval);
    }

    @Test
    void shouldNotOverrideAssertionTimeoutIfNotSetInConfig() throws Exception {
      // given
      final Duration assertionTimeout = Duration.ofMinutes(1);
      final Duration assertionInterval = Duration.ofMillis(50);

      CamundaAssert.setAssertionTimeout(assertionTimeout);
      CamundaAssert.setAssertionInterval(assertionInterval);

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      final CamundaAssertAwaitBehavior awaitBehavior = CamundaAssert.getAwaitBehavior();
      assertThat(awaitBehavior.getAssertionTimeout()).isEqualTo(assertionTimeout);
      assertThat(awaitBehavior.getAssertionInterval()).isEqualTo(assertionInterval);
    }
  }

  @Nested
  class JudgeConfigurationTest {

    private @Mock JudgeConfig judgeConfig;

    @AfterEach
    void clearConfig() {
      CamundaAssert.setJudgeConfig(null);
    }

    @Test
    void shouldBootstrapJudgeConfigFromServiceLoader() throws Exception {
      // given: the FakeChatModelAdapterProvider is registered in the file
      // `META-INF/services/io.camunda.process.test.api.judge.ChatModelAdapterProvider`

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getJudgeConfig())
          .as("JudgeConfig should be bootstrapped from Java ServiceLoader")
          .isNotNull()
          .satisfies(
              judgeConfig ->
                  assertThat(judgeConfig.getChatModel().generate("anything"))
                      .isEqualTo(FakeChatModelAdapterProvider.FAKE_REASONING))
          .satisfies(judgeConfig -> assertThat(judgeConfig.getThreshold()).isEqualTo(0.8));
    }

    @Test
    void shouldSetJudgeConfigOnExtension() throws Exception {
      // given
      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP)
              .withJudgeConfig(judgeConfig);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getJudgeConfig()).isEqualTo(judgeConfig);
    }

    @Test
    void shouldSetJudgeConfigOnAssertion() throws Exception {
      // given
      CamundaAssert.setJudgeConfig(judgeConfig);

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getJudgeConfig()).isEqualTo(judgeConfig);
    }
  }

  @Nested
  class SemanticSimilarityConfigurationTest {

    private @Mock SemanticSimilarityConfig semanticSimilarityConfig;

    @AfterEach
    void clearConfig() {
      CamundaAssert.setSemanticSimilarityConfig(null);
    }

    @Test
    void shouldBootstrapSemanticSimilarityConfigFromServiceLoader() throws Exception {
      // given: the FakeEmbeddingModelAdapterProvider is registered in the file
      // `META-INF/services/io.camunda.process.test.api.similarity.EmbeddingModelAdapterProvider`

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getSemanticSimilarityConfig())
          .as("SemanticSimilarityConfig should be auto-bootstrapped via SPI")
          .isNotNull()
          .satisfies(
              config ->
                  assertThat(config.getEmbeddingModel().embed("any text"))
                      .isEqualTo(new float[] {1.0f, 0.0f}))
          .satisfies(config -> assertThat(config.getThreshold()).isEqualTo(0.9));
    }

    @Test
    void shouldSetSemanticSimilarityConfigOnExtension() throws Exception {
      // given
      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP)
              .withSemanticSimilarityConfig(semanticSimilarityConfig);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getSemanticSimilarityConfig()).isEqualTo(semanticSimilarityConfig);
    }

    @Test
    void shouldSetSemanticSimilarityConfigOnAssertion() throws Exception {
      // given
      CamundaAssert.setSemanticSimilarityConfig(semanticSimilarityConfig);

      final CamundaProcessTestExtension extension =
          new CamundaProcessTestExtension(camundaRuntimeBuilder, processCoverageBuilder, NOOP);

      // when
      extension.beforeAll(extensionContext);
      extension.beforeEach(extensionContext);

      // then
      assertThat(CamundaAssert.getSemanticSimilarityConfig()).isEqualTo(semanticSimilarityConfig);
    }
  }
}
