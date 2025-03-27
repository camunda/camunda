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
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultPrinter;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.zeebe.client.ZeebeClient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
 *   <li>Close created {@link CamundaClient}s
 *   <li>Purge the runtime (i.e. delete all data)
 * </ul>
 *
 * <p>The container runtime is closed once all tests have run.
 */
public class CamundaProcessTestExtension
    implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback {

  /** The JUnit extension namespace to store the runtime and context. */
  public static final Namespace NAMESPACE = Namespace.create(CamundaProcessTestExtension.class);

  /** The JUnit extension store key of the runtime. */
  public static final String STORE_KEY_RUNTIME = "camunda-process-test-runtime";

  /** The JUnit extension store key of the context. */
  public static final String STORE_KEY_CONTEXT = "camunda-process-test-context";

  private final List<AutoCloseable> createdClients = new ArrayList<>();

  private final CamundaContainerRuntimeBuilder containerRuntimeBuilder;
  private final CamundaProcessTestResultPrinter processTestResultPrinter;

  private CamundaContainerRuntime containerRuntime;
  private CamundaProcessTestResultCollector processTestResultCollector;

  private CamundaManagementClient camundaManagementClient;
  private CamundaProcessTestContext camundaProcessTestContext;

  CamundaProcessTestExtension(
      final CamundaContainerRuntimeBuilder containerRuntimeBuilder,
      final Consumer<String> testResultPrintStream) {
    this.containerRuntimeBuilder = containerRuntimeBuilder;
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
    this(CamundaContainerRuntime.newBuilder(), System.err::println);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    // create runtime
    containerRuntime = containerRuntimeBuilder.build();
    containerRuntime.start();

    camundaManagementClient =
        new CamundaManagementClient(
            containerRuntime.getCamundaContainer().getMonitoringApiAddress(),
            containerRuntime.getCamundaContainer().getRestApiAddress());

    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            containerRuntime.getCamundaContainer(),
            containerRuntime.getConnectorsContainer(),
            createdClients::add,
            camundaManagementClient);

    // put in store
    final Store store = context.getStore(NAMESPACE);
    store.put(STORE_KEY_RUNTIME, containerRuntime);
    store.put(STORE_KEY_CONTEXT, camundaProcessTestContext);
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    // inject fields
    try {
      injectField(context, CamundaClient.class, camundaProcessTestContext::createClient);
      injectField(context, ZeebeClient.class, camundaProcessTestContext::createZeebeClient);
      injectField(context, CamundaProcessTestContext.class, () -> camundaProcessTestContext);
    } catch (final Exception e) {
      closeCreatedClients();
      containerRuntime.close();
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
  public void afterEach(final ExtensionContext extensionContext) throws Exception {
    // collect test results
    final ProcessTestResult testResult = processTestResultCollector.collect();

    // reset assertions
    CamundaAssert.reset();
    // close all created clients
    closeCreatedClients();
    // purge cluster
    camundaManagementClient.purgeCluster();

    // print test results
    if (isTestFailed(extensionContext)) {
      processTestResultPrinter.print(testResult);
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) throws Exception {
    // close the runtime
    containerRuntime.close();
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
   */
  public CamundaProcessTestExtension withCamundaVersion(final String camundaVersion) {
    containerRuntimeBuilder
        .withCamundaDockerImageVersion(camundaVersion)
        .withConnectorsDockerImageVersion(camundaVersion);
    return this;
  }

  /**
   * Configure the Camunda Docker image name of the runtime.
   *
   * @param dockerImageName the Docker image name to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaDockerImageName(final String dockerImageName) {
    containerRuntimeBuilder.withCamundaDockerImageName(dockerImageName);
    return this;
  }

  /**
   * Add environment variables to the Camunda runtime.
   *
   * @param envVars environment variables to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaEnv(final Map<String, String> envVars) {
    containerRuntimeBuilder.withCamundaEnv(envVars);
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
    containerRuntimeBuilder.withCamundaEnv(name, value);
    return this;
  }

  /**
   * Add an exposed port to the Camunda runtime.
   *
   * @param port the port to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withCamundaExposedPort(final int port) {
    containerRuntimeBuilder.withCamundaExposedPort(port);
    return this;
  }

  /**
   * Enable or disable the Connectors. By default, the Connectors are disabled.
   *
   * @param enabled set {@code true} to enable the Connectors
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsEnabled(final boolean enabled) {
    containerRuntimeBuilder.withConnectorsEnabled(enabled);
    return this;
  }

  /**
   * Configure the Connectors Docker image name of the runtime.
   *
   * @param dockerImageName the Docker image name to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsDockerImageName(final String dockerImageName) {
    containerRuntimeBuilder.withConnectorsDockerImageName(dockerImageName);
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
    containerRuntimeBuilder.withConnectorsDockerImageVersion(dockerImageVersion);
    return this;
  }

  /**
   * Add environment variables to the Connectors runtime.
   *
   * @param envVars environment variables to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withConnectorsEnv(final Map<String, String> envVars) {
    containerRuntimeBuilder.withConnectorsEnv(envVars);
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
    containerRuntimeBuilder.withConnectorsEnv(name, value);
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
    containerRuntimeBuilder.withConnectorsSecret(name, value);
    return this;
  }

  private void closeCreatedClients() throws Exception {
    for (final AutoCloseable client : createdClients) {
      client.close();
    }
  }
}
