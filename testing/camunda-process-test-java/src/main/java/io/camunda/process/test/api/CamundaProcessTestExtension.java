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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.coverage.ProcessCoverage;
import io.camunda.process.test.api.coverage.ProcessCoverageBuilder;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultPrinter;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.zeebe.client.ZeebeClient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JUnit extension that provides the runtime for process tests.
 *
 * <p>The container runtime starts before any tests have run.
 *
 * <p>Before each test method:
 *
 * <ul>
 *   <li>Inject a {@link CamundaClient} to a field in the test class
 *   <li>Inject a {@link CamundaProcessTestContext} to a field in the test class
 * </ul>
 *
 * <p>After each test method:
 *
 * <ul>
 *   <li>Collect process coverage data
 *   <li>Close created {@link CamundaClient}s
 *   <li>Purge the runtime (i.e. delete all data)
 * </ul>
 *
 * <p>After all tests have run:
 *
 * <ul>
 *   <li>Report process coverage (JSON)
 *   <li>Close container runtime
 * </ul>
 */
public class CamundaProcessTestExtension
    implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback {

  /** The JUnit extension namespace to store the runtime and context. */
  public static final Namespace NAMESPACE = Namespace.create(CamundaProcessTestExtension.class);

  /** The JUnit extension store key of the runtime. */
  public static final String STORE_KEY_RUNTIME = "camunda-process-test-runtime";

  /** The JUnit extension store key of the context. */
  public static final String STORE_KEY_CONTEXT = "camunda-process-test-context";

  private static final Logger LOG = LoggerFactory.getLogger(CamundaProcessTestExtension.class);

  private final List<AutoCloseable> createdClients = new ArrayList<>();

  private final CamundaProcessTestRuntimeBuilder runtimeBuilder;
  private final CamundaProcessTestResultPrinter processTestResultPrinter;
  private final ProcessCoverageBuilder processCoverageBuilder;
  private ProcessCoverage processCoverage;

  private CamundaProcessTestRuntime runtime;
  private CamundaProcessTestResultCollector processTestResultCollector;

  private CamundaManagementClient camundaManagementClient;
  private CamundaProcessTestContext camundaProcessTestContext;

  CamundaProcessTestExtension(
      final CamundaProcessTestRuntimeBuilder containerRuntimeBuilder,
      final ProcessCoverageBuilder processCoverageBuilder,
      final Consumer<String> testResultPrintStream) {
    runtimeBuilder = containerRuntimeBuilder;
    this.processCoverageBuilder = processCoverageBuilder.printStream(testResultPrintStream);
    processTestResultPrinter = new CamundaProcessTestResultPrinter(testResultPrintStream);
  }

  /**
   * Creates a new instance of the extension. Can be used to configure the runtime.
   *
   * <p>Example usage:
   *
   * <pre>
   * public class MyProcessTest {
   *
   *   &#064;RegisterExtension
   *   public CamundaProcessTestExtension extension =
   *       new CamundaProcessTestExtension().withCamundaVersion("8.6.0");
   *
   * }
   * </pre>
   */
  public CamundaProcessTestExtension() {
    this(CamundaProcessTestContainerRuntime.newBuilder(), ProcessCoverage.newBuilder(), LOG::info);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    // create runtime
    runtime = runtimeBuilder.build();
    runtime.start();

    camundaManagementClient =
        new CamundaManagementClient(
            runtime.getCamundaMonitoringApiAddress(), runtime.getCamundaRestApiAddress());

    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            runtime,
            createdClients::add,
            camundaManagementClient,
            CamundaAssert.getAwaitBehavior());

    // create process coverage
    processCoverage =
        processCoverageBuilder
            .testClass(context.getRequiredTestClass())
            .dataSource(() -> new CamundaDataSource(camundaProcessTestContext.createClient()))
            .build();

    // put in store
    final Store store = context.getStore(NAMESPACE);
    store.put(STORE_KEY_RUNTIME, runtime);
    store.put(STORE_KEY_CONTEXT, camundaProcessTestContext);
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    if (runtime == null) {
      throw new IllegalStateException(
          "The CamundaProcessTestExtension failed to start because the runtime is not created. "
              + "Make sure that you registering the extension on a static field.");
    }

    // inject fields
    try {
      injectField(context, CamundaClient.class, camundaProcessTestContext::createClient);
      injectField(context, ZeebeClient.class, camundaProcessTestContext::createZeebeClient);
      injectField(context, CamundaProcessTestContext.class, () -> camundaProcessTestContext);
    } catch (final Exception e) {
      closeCreatedClients();
      runtime.close();
      throw e;
    }

    // initialize assertions
    final CamundaDataSource dataSource =
        new CamundaDataSource(camundaProcessTestContext.createClient());
    CamundaAssert.initialize(dataSource);

    // initialize result collector
    processTestResultCollector = new CamundaProcessTestResultCollector(dataSource);
  }

  private <T> void injectField(
      final ExtensionContext context,
      final Class<T> injectionType,
      final Supplier<T> injectionValue) {
    context
        .getRequiredTestInstances()
        .getAllInstances()
        .forEach(instance -> injectField(instance, injectionType, injectionValue));
  }

  private <T> void injectField(
      final Object testInstance, final Class<T> injectionType, final Supplier<T> injectionValue) {
    ReflectionUtils.findFields(
            testInstance.getClass(),
            field -> isNotStatic(field) && field.getType().isAssignableFrom(injectionType),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                field.setAccessible(true);
                field.set(testInstance, injectionValue.get());
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }

  private static boolean isNotStatic(final Field field) {
    return !Modifier.isStatic(field.getModifiers());
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    if (runtime == null) {
      // Skip if the runtime is not created.
      return;
    }

    try {
      processCoverage.collectTestRunCoverage(context.getDisplayName());
    } catch (final Throwable t) {
      LOG.warn("Failed to collect test process coverage, skipping.", t);
    }

    if (isTestFailed(context)) {
      printTestResults();
    }
    CamundaAssert.reset();
    closeCreatedClients();
    // final steps: reset the time and delete data
    // It's important that the runtime clock is reset before the purge is started, as doing it
    // the other way around leads to race conditions and inconsistencies in the tests
    resetRuntimeClock();
    deleteRuntimeData();
  }

  private void printTestResults() {
    try {
      // collect test results
      final ProcessTestResult testResult = processTestResultCollector.collect();
      // print test results
      processTestResultPrinter.print(testResult);
    } catch (final Throwable t) {
      LOG.warn("Failed to collect test results, skipping.", t);
    }
  }

  private void deleteRuntimeData() {
    try {
      LOG.debug("Deleting the runtime data");
      final Instant startTime = Instant.now();

      camundaManagementClient.purgeCluster();
      final Instant endTime = Instant.now();
      final Duration duration = Duration.between(startTime, endTime);
      LOG.debug("Runtime data deleted in {}", duration);

    } catch (final Throwable t) {
      LOG.warn(
          "Failed to delete the runtime data, skipping. Check the runtime for details. "
              + "Note that a dirty runtime may cause failures in other test cases.",
          t);
    }
  }

  private void resetRuntimeClock() {
    try {
      LOG.debug("Resetting the time");
      camundaManagementClient.resetTime();
      LOG.debug("Time reset");
    } catch (final Throwable t) {
      LOG.warn(
          "Failed to reset the time, skipping. Check the runtime for details. "
              + "Note that a dirty runtime may cause failures in other test cases.",
          t);
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    if (runtime == null) {
      // Skip if the runtime is not created.
      return;
    }

    try {
      processCoverage.reportCoverage();
    } catch (final Throwable t) {
      LOG.warn("Failed to report process coverage, skipping.", t);
    }
    runtime.close();
  }

  private static boolean isTestFailed(final ExtensionContext extensionContext) {
    return extensionContext.getExecutionException().isPresent();
  }

  // ============ Configuration options =================

  /**
   * Configure the Camunda version of the runtime.
   *
   * @param camundaVersion the version to use
   * @return the extension builder
   * @deprecated use withCamundaDockerImageVersion instead.
   * @since 8.8.0
   */
  @Deprecated
  public CamundaProcessTestExtension withCamundaVersion(final String camundaVersion) {
    return withCamundaDockerImageVersion(camundaVersion);
  }

  /**
   * Configure the Camunda docker image version of the runtime.
   *
   * @param camundaDockerImageVersion the version to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaDockerImageVersion(
      final String camundaDockerImageVersion) {

    runtimeBuilder.withCamundaDockerImageVersion(camundaDockerImageVersion);
    return this;
  }

  /**
   * Configure the Camunda Docker image name of the runtime.
   *
   * @param dockerImageName the Docker image name to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaDockerImageName(final String dockerImageName) {
    runtimeBuilder.withCamundaDockerImageName(dockerImageName);
    return this;
  }

  /**
   * Add environment variables to the Camunda runtime.
   *
   * @param envVars environment variables to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaEnv(final Map<String, String> envVars) {
    runtimeBuilder.withCamundaEnv(envVars);
    return this;
  }

  /**
   * Add an environment variable to the Camunda runtime.
   *
   * @param name the variable name
   * @param value the variable value
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaEnv(final String name, final String value) {
    runtimeBuilder.withCamundaEnv(name, value);
    return this;
  }

  /**
   * Add an exposed port to the Camunda runtime.
   *
   * @param port the port to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaExposedPort(final int port) {
    runtimeBuilder.withCamundaExposedPort(port);
    return this;
  }

  /**
   * Enable or disable the Connectors. By default, the Connectors are disabled.
   *
   * @param enabled set {@code true} to enable the Connectors
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsEnabled(final boolean enabled) {
    runtimeBuilder.withConnectorsEnabled(enabled);
    return this;
  }

  /**
   * Configure the Connectors Docker image name of the runtime.
   *
   * @param dockerImageName the Docker image name to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsDockerImageName(final String dockerImageName) {
    runtimeBuilder.withConnectorsDockerImageName(dockerImageName);
    return this;
  }

  /**
   * Configure the Connectors Docker image version of the runtime.
   *
   * @param dockerImageVersion the version to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsDockerImageVersion(
      final String dockerImageVersion) {
    runtimeBuilder.withConnectorsDockerImageVersion(dockerImageVersion);
    return this;
  }

  /**
   * Add environment variables to the Connectors runtime.
   *
   * @param envVars environment variables to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsEnv(final Map<String, String> envVars) {
    runtimeBuilder.withConnectorsEnv(envVars);
    return this;
  }

  /**
   * Add an environment variable to the Connectors runtime.
   *
   * @param name the variable name
   * @param value the variable value
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsEnv(final String name, final String value) {
    runtimeBuilder.withConnectorsEnv(name, value);
    return this;
  }

  /**
   * Add a secret to the Connectors runtime.
   *
   * @param name the name of the secret
   * @param value the value of the secret
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsSecret(final String name, final String value) {
    runtimeBuilder.withConnectorsSecret(name, value);
    return this;
  }

  /**
   * Configure the mode of the runtime (managed/remote).
   *
   * @param runtimeMode the runtime mode to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withRuntimeMode(
      final CamundaProcessTestRuntimeMode runtimeMode) {
    runtimeBuilder.withRuntimeMode(runtimeMode);
    return this;
  }

  /**
   * Configure the connection to the remote runtime using the given client builder.
   *
   * @param camundaClientBuilderFactory the client builder to configure the connection
   * @return the extension builder
   */
  public CamundaProcessTestExtension withRemoteCamundaClientBuilderFactory(
      final CamundaClientBuilderFactory camundaClientBuilderFactory) {
    runtimeBuilder.withRemoteCamundaClientBuilderFactory(camundaClientBuilderFactory);
    return this;
  }

  /**
   * Configure the address to the remote runtime's monitoring API.
   *
   * @param remoteCamundaMonitoringApiAddress the address of the monitoring API
   * @return the extension builder
   */
  public CamundaProcessTestExtension withRemoteCamundaMonitoringApiAddress(
      final URI remoteCamundaMonitoringApiAddress) {
    runtimeBuilder.withRemoteCamundaMonitoringApiAddress(remoteCamundaMonitoringApiAddress);
    return this;
  }

  /**
   * Configure the address to the remote Connectors REST API.
   *
   * @param remoteConnectorsRestApiAddress the address of the Connectors REST API
   * @return the extension builder
   */
  public CamundaProcessTestExtension withRemoteConnectorsRestApiAddress(
      final URI remoteConnectorsRestApiAddress) {
    runtimeBuilder.withRemoteConnectorsRestApiAddress(remoteConnectorsRestApiAddress);
    return this;
  }

  /**
   * Specifies process definition keys that should be excluded from coverage analysis. These
   * processes will not be considered when calculating coverage metrics.
   *
   * @param processDefinitionIds an array of process definition ids to exclude
   * @return the extension builder
   */
  public CamundaProcessTestExtension excludeProcessDefinitionIds(
      final String... processDefinitionIds) {
    processCoverageBuilder.excludeProcessDefinitionIds(processDefinitionIds);
    return this;
  }

  /**
   * Specifies the output directory for coverage reports. Coverage reports will be generated and
   * saved to this directory after test execution.
   *
   * @param reportDirectory the directory path where reports should be saved (defaults to
   *     "target/process-test-coverage/")
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCoverageReportDirectory(final String reportDirectory) {
    processCoverageBuilder.reportDirectory(reportDirectory);
    return this;
  }

  private void closeCreatedClients() {
    for (final AutoCloseable client : createdClients) {
      try {
        client.close();
      } catch (final Exception e) {
        LOG.debug("Failed to close client, continue.", e);
      }
    }
  }
}
