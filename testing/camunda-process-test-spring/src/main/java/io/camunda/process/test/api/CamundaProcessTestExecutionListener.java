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
import io.camunda.client.api.JsonMapper;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.configuration.CamundaContainerRuntimeConfiguration;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.ZeebeClientProxy;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaContainerRuntimeBuilder;
import io.camunda.process.test.impl.runtime.CamundaRuntime;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultPrinter;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
import io.camunda.spring.client.event.CamundaClientClosingEvent;
import io.camunda.spring.client.event.CamundaClientCreatedEvent;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.event.ZeebeClientClosingEvent;
import io.camunda.zeebe.spring.client.event.ZeebeClientCreatedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * A Spring test execution listener that provides the runtime for process tests.
 *
 * <p>The container runtime starts before any tests have run.
 *
 * <p>Before each test method:
 *
 * <ul>
 *   <li>Create a {@link CamundaClient} to inject in the test class
 *   <li>Create a {@link CamundaProcessTestContext} to inject in the test class
 *   <li>Publish a {@link CamundaClientCreatedEvent}
 * </ul>
 *
 * <p>After each test method:
 *
 * <ul>
 *   <li>Publish a {@link CamundaClientClosingEvent}
 *   <li>Close created {@link CamundaClient}s
 *   <li>Purge the runtime (i.e. delete all data)
 * </ul>
 *
 * <p>The container runtime is closed once all tests have run.
 */
public class CamundaProcessTestExecutionListener implements TestExecutionListener, Ordered {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaProcessTestExecutionListener.class);

  private final CamundaContainerRuntimeBuilder containerRuntimeBuilder;
  private final CamundaProcessTestResultPrinter processTestResultPrinter;
  private final List<AutoCloseable> createdClients = new ArrayList<>();

  private CamundaRuntime runtime;
  private CamundaProcessTestResultCollector processTestResultCollector;
  private CamundaProcessTestContext camundaProcessTestContext;
  private CamundaManagementClient camundaManagementClient;
  private CamundaClient client;
  private ZeebeClient zeebeClient;

  public CamundaProcessTestExecutionListener() {
    this(CamundaContainerRuntime.newBuilder(), System.err::println);
  }

  CamundaProcessTestExecutionListener(
      final CamundaContainerRuntimeBuilder containerRuntimeBuilder,
      final Consumer<String> testResultPrintStream) {
    this.containerRuntimeBuilder = containerRuntimeBuilder;
    processTestResultPrinter = new CamundaProcessTestResultPrinter(testResultPrintStream);
  }

  @Override
  public void beforeTestClass(final TestContext testContext) {
    // create runtime
    runtime = buildRuntime(testContext);
    runtime.start();

    camundaManagementClient =
        new CamundaManagementClient(
            runtime.getCamundaMonitoringApiAddress(), runtime.getCamundaRestApiAddress());

    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            runtime.getCamundaRestApiAddress(),
            runtime.getCamundaGrpcApiAddress(),
            runtime.getConnectorsRestApiAddress(),
            createdClients::add,
            camundaManagementClient);
  }

  @Override
  public void beforeTestMethod(final TestContext testContext) {
    client = createClient(testContext, camundaProcessTestContext);
    zeebeClient = createZeebeClient(testContext, camundaProcessTestContext);

    // fill proxies
    testContext.getApplicationContext().getBean(CamundaClientProxy.class).setClient(client);
    testContext.getApplicationContext().getBean(ZeebeClientProxy.class).setClient(zeebeClient);
    testContext
        .getApplicationContext()
        .getBean(CamundaProcessTestContextProxy.class)
        .setContext(camundaProcessTestContext);

    // publish Zeebe client
    testContext.getApplicationContext().publishEvent(new CamundaClientCreatedEvent(this, client));
    testContext
        .getApplicationContext()
        .publishEvent(new ZeebeClientCreatedEvent(this, zeebeClient));

    // initialize assertions
    final CamundaDataSource dataSource = new CamundaDataSource(client);
    CamundaAssert.initialize(dataSource);

    // initialize result collector
    processTestResultCollector = new CamundaProcessTestResultCollector(dataSource);
  }

  @Override
  public void afterTestMethod(final TestContext testContext) throws Exception {
    if (runtime == null) {
      // Skip if the runtime is not created.
      return;
    }

    if (isTestFailed(testContext)) {
      printTestResults();
    }
    // reset assertions
    CamundaAssert.reset();
    // close Zeebe clients
    testContext.getApplicationContext().publishEvent(new CamundaClientClosingEvent(this, client));
    testContext
        .getApplicationContext()
        .publishEvent(new ZeebeClientClosingEvent(this, zeebeClient));

    closeCreatedClients();

    // clean up proxies
    testContext.getApplicationContext().getBean(CamundaClientProxy.class).removeClient();
    testContext.getApplicationContext().getBean(ZeebeClientProxy.class).removeClient();
    testContext
        .getApplicationContext()
        .getBean(CamundaProcessTestContextProxy.class)
        .removeContext();

    // final step: delete data
    deleteRuntimeData();
  }

  @Override
  public void afterTestClass(final TestContext testContext) throws Exception {
    if (runtime == null) {
      // Skip if the runtime is not created.
      return;
    }
    runtime.close();
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

  private void closeCreatedClients() {
    for (final AutoCloseable client : createdClients) {
      try {
        client.close();
      } catch (final Exception e) {
        LOG.debug("Failed to close client, continue.", e);
      }
    }
  }

  private CamundaRuntime buildRuntime(final TestContext testContext) {
    final CamundaContainerRuntimeConfiguration runtimeConfiguration =
        testContext.getApplicationContext().getBean(CamundaContainerRuntimeConfiguration.class);

    containerRuntimeBuilder
        .withCamundaDockerImageVersion(runtimeConfiguration.getCamundaVersion())
        .withCamundaDockerImageName(runtimeConfiguration.getCamundaDockerImageName())
        .withCamundaEnv(runtimeConfiguration.getCamundaEnvVars());

    runtimeConfiguration
        .getCamundaExposedPorts()
        .forEach(containerRuntimeBuilder::withCamundaExposedPort);

    containerRuntimeBuilder
        .withConnectorsEnabled(runtimeConfiguration.isConnectorsEnabled())
        .withConnectorsDockerImageName(runtimeConfiguration.getConnectorsDockerImageName())
        .withConnectorsDockerImageVersion(runtimeConfiguration.getConnectorsDockerImageVersion())
        .withConnectorsEnv(runtimeConfiguration.getConnectorsEnvVars())
        .withConnectorsSecrets(runtimeConfiguration.getConnectorsSecrets());

    containerRuntimeBuilder
        .withRuntimeMode(runtimeConfiguration.getRuntimeMode())
        .withRemoteCamundaMonitoringApiAddress(
            runtimeConfiguration.getRemote().getCamundaMonitoringApiAddress())
        .withRemoteConnectorsRestApiAddress(
            runtimeConfiguration.getRemote().getConnectorsRestApiAddress());

    final CamundaClientBuilderFactory camundaClientBuilderFactory =
        testContext.getApplicationContext().getBean(CamundaClientBuilderFactory.class);
    containerRuntimeBuilder.withCamundaClientBuilder(camundaClientBuilderFactory);

    return containerRuntimeBuilder.build();
  }

  private static CamundaClient createClient(
      final TestContext testContext, final CamundaProcessTestContext camundaProcessTestContext) {
    return camundaProcessTestContext.createClient(
        builder -> {
          if (hasBeanForType(testContext, JsonMapper.class)) {
            final JsonMapper jsonMapper =
                testContext.getApplicationContext().getBean(JsonMapper.class);
            builder.withJsonMapper(jsonMapper);
          }
        });
  }

  private static ZeebeClient createZeebeClient(
      final TestContext testContext, final CamundaProcessTestContext camundaProcessTestContext) {
    return camundaProcessTestContext.createZeebeClient(
        builder -> {
          if (hasBeanForType(testContext, io.camunda.zeebe.client.api.JsonMapper.class)) {
            builder.withJsonMapper(
                testContext
                    .getApplicationContext()
                    .getBean(io.camunda.zeebe.client.api.JsonMapper.class));
          }
        });
  }

  private static boolean hasBeanForType(final TestContext testContext, final Class<?> type) {
    return testContext.getApplicationContext().getBeanNamesForType(type).length > 0;
  }

  private static boolean isTestFailed(final TestContext testContext) {
    return testContext.getTestException() != null;
  }

  @Override
  public int getOrder() {
    return Integer.MAX_VALUE;
  }
}
