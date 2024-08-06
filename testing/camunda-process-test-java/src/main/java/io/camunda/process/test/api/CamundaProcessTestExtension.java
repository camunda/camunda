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

import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.containers.OperateContainer;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * A JUnit extension that provides the runtime for process tests.
 *
 * <p>Before each test method:
 *
 * <ul>
 *   <li>Start the runtime
 *   <li>Inject a {@link ZeebeClient} to a field in the test class
 *   <li>Inject a {@link CamundaProcessTestContext} to a field in the test class
 * </ul>
 *
 * <p>After each test method:
 *
 * <ul>
 *   <li>Close created {@link ZeebeClient}s
 *   <li>Stop the runtime
 * </ul>
 */
public class CamundaProcessTestExtension implements BeforeEachCallback, AfterEachCallback {

  /** The JUnit extension namespace to store the runtime and context. */
  public static final Namespace NAMESPACE = Namespace.create(CamundaProcessTestExtension.class);

  /** The JUnit extension store key of the runtime. */
  public static final String STORE_KEY_RUNTIME = "camunda-process-test-runtime";

  /** The JUnit extension store key of the context. */
  public static final String STORE_KEY_CONTEXT = "camunda-process-test-context";

  private final List<ZeebeClient> createdClients = new ArrayList<>();

  private final CamundaContainerRuntimeBuilder containerRuntimeBuilder;

  private CamundaContainerRuntime containerRuntime;

  CamundaProcessTestExtension(final CamundaContainerRuntimeBuilder containerRuntimeBuilder) {
    this.containerRuntimeBuilder = containerRuntimeBuilder;
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
    this(CamundaContainerRuntime.newBuilder());
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    // create runtime
    containerRuntime = containerRuntimeBuilder.build();
    containerRuntime.start();

    final CamundaProcessTestContext camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            containerRuntime.getZeebeContainer(), createdClients::add);

    // inject fields
    try {
      injectField(context, ZeebeClient.class, camundaProcessTestContext::createClient);
      injectField(context, CamundaProcessTestContext.class, () -> camundaProcessTestContext);
    } catch (final Exception e) {
      createdClients.forEach(ZeebeClient::close);
      containerRuntime.close();
      throw e;
    }

    // put in store
    final Store store = context.getStore(NAMESPACE);
    store.put(STORE_KEY_RUNTIME, containerRuntime);
    store.put(STORE_KEY_CONTEXT, camundaProcessTestContext);

    // initialize assertions
    final CamundaDataSource dataSource = createDataSource(containerRuntime);
    CamundaAssert.initialize(dataSource);
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
            field ->
                ReflectionUtils.isNotStatic(field)
                    && field.getType().isAssignableFrom(injectionType),
            ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
        .forEach(
            field -> {
              try {
                makeAccessible(field).set(testInstance, injectionValue.get());
              } catch (final Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
              }
            });
  }

  private CamundaDataSource createDataSource(final CamundaContainerRuntime containerRuntime) {
    final OperateContainer operateContainer = containerRuntime.getOperateContainer();
    final String operateApiEndpoint =
        "http://" + operateContainer.getHost() + ":" + operateContainer.getRestApiPort();
    return new CamundaDataSource(operateApiEndpoint);
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) throws Exception {
    // reset assertions
    CamundaAssert.reset();
    // close all created clients
    createdClients.forEach(ZeebeClient::close);
    // close the runtime
    containerRuntime.close();
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
        .withZeebeDockerImageVersion(camundaVersion)
        .withOperateDockerImageVersion(camundaVersion)
        .withTasklistDockerImageVersion(camundaVersion);
    return this;
  }

  /**
   * Configure the Zeebe Docker image name of the runtime.
   *
   * @param dockerImageName the Docker image name to use
   * @return the extension builder
   */
  public CamundaProcessTestExtension withZeebeDockerImageName(final String dockerImageName) {
    containerRuntimeBuilder.withZeebeDockerImageName(dockerImageName);
    return this;
  }

  /**
   * Add environment variables to the Zeebe runtime.
   *
   * @param envVars environment variables to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withZeebeEnv(final Map<String, String> envVars) {
    containerRuntimeBuilder.withZeebeEnv(envVars);
    return this;
  }

  /**
   * Add an environment variable to the Zeebe runtime.
   *
   * @param name the variable name
   * @param value the variable value
   * @return the extension builder
   */
  public CamundaProcessTestExtension withZeebeEnv(final String name, final String value) {
    containerRuntimeBuilder.withZeebeEnv(name, value);
    return this;
  }

  /**
   * Add an exposed port to the Zeebe runtime.
   *
   * @param port the port to add
   * @return the extension builder
   */
  public CamundaProcessTestExtension withZeebeExposedPort(final int port) {
    containerRuntimeBuilder.withZeebeExposedPort(port);
    return this;
  }
}
