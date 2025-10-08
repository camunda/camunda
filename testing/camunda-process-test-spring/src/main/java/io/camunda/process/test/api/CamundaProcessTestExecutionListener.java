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
import io.camunda.client.spring.event.CamundaClientClosingSpringEvent;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.CoverageReportConfiguration;
import io.camunda.process.test.impl.containers.CamundaContainer.MultiTenancyConfiguration;
import io.camunda.process.test.impl.coverage.ProcessCoverage;
import io.camunda.process.test.impl.coverage.ProcessCoverageBuilder;
import io.camunda.process.test.impl.deployment.TestDeploymentService;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.ZeebeClientProxy;
import io.camunda.process.test.impl.runtime.CamundaProcessTestContainerRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.runtime.CamundaSpringProcessTestRuntimeBuilder;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultCollector;
import io.camunda.process.test.impl.testresult.CamundaProcessTestResultPrinter;
import io.camunda.process.test.impl.testresult.ProcessTestResult;
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
 *   <li>Publish a {@link CamundaClientCreatedSpringEvent}
 * </ul>
 *
 * <p>After each test method:
 *
 * <ul>
 *   <li>Publish a {@link CamundaClientClosingSpringEvent}
 *   <li>Close created {@link CamundaClient}s
 *   <li>Purge the runtime (i.e. delete all data)
 * </ul>
 *
 * <p>The container runtime is closed once all tests have run.
 */
public class CamundaProcessTestExecutionListener implements TestExecutionListener, Ordered {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaProcessTestExecutionListener.class);

  private final CamundaProcessTestRuntimeBuilder containerRuntimeBuilder;
  private final CamundaProcessTestResultPrinter processTestResultPrinter;
  private final ProcessCoverageBuilder processCoverageBuilder;
  private final TestDeploymentService testDeploymentService;
  private final List<AutoCloseable> createdClients = new ArrayList<>();

  private ProcessCoverage processCoverage;
  private CamundaProcessTestRuntime runtime;
  private CamundaProcessTestResultCollector processTestResultCollector;
  private CamundaProcessTestContext camundaProcessTestContext;
  private CamundaManagementClient camundaManagementClient;
  private CamundaClient client;
  private ZeebeClient zeebeClient;

  public CamundaProcessTestExecutionListener() {
    this(CamundaProcessTestContainerRuntime.newBuilder(), ProcessCoverage.newBuilder(), LOG::info);
  }

  CamundaProcessTestExecutionListener(
      final CamundaProcessTestRuntimeBuilder containerRuntimeBuilder,
      final ProcessCoverageBuilder processCoverageBuilder,
      final Consumer<String> testResultPrintStream) {
    this.containerRuntimeBuilder = containerRuntimeBuilder;
    this.processCoverageBuilder = processCoverageBuilder.printStream(testResultPrintStream);
    processTestResultPrinter = new CamundaProcessTestResultPrinter(testResultPrintStream);
    testDeploymentService = new TestDeploymentService();
  }

  @Override
  public void beforeTestClass(final TestContext testContext) {
    final CamundaProcessTestRuntimeConfiguration runtimeConfiguration =
        testContext.getApplicationContext().getBean(CamundaProcessTestRuntimeConfiguration.class);

    final JsonMapper jsonMapper = testContext.getApplicationContext().getBean(JsonMapper.class);
    final io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper =
        testContext.getApplicationContext().getBean(io.camunda.zeebe.client.api.JsonMapper.class);

    // create runtime
    runtime = buildRuntime(testContext, runtimeConfiguration);
    runtime.start();

    camundaManagementClient = createManagementClient(runtimeConfiguration);

    camundaProcessTestContext =
        new CamundaProcessTestContextImpl(
            runtime,
            createdClients::add,
            camundaManagementClient,
            CamundaAssert.getAwaitBehavior(),
            jsonMapper,
            zeebeJsonMapper);

    // create process coverage
    final CoverageReportConfiguration coverageReportConfiguration =
        runtimeConfiguration.getCoverage();
    processCoverage =
        processCoverageBuilder
            .testClass(testContext.getTestClass())
            .dataSource(() -> new CamundaDataSource(camundaProcessTestContext.createClient()))
            .reportDirectory(coverageReportConfiguration.getReportDirectory())
            .excludeProcessDefinitionIds(coverageReportConfiguration.getExcludedProcesses())
            .build();

    // initialize json mapper
    initializeJsonMapper(jsonMapper, zeebeJsonMapper);
  }

  @Override
  public void beforeTestMethod(final TestContext testContext) {
    client = createClient(camundaProcessTestContext);
    zeebeClient = createZeebeClient(camundaProcessTestContext);

    // fill proxies
    testContext.getApplicationContext().getBean(CamundaClientProxy.class).setClient(client);
    testContext.getApplicationContext().getBean(ZeebeClientProxy.class).setClient(zeebeClient);
    testContext
        .getApplicationContext()
        .getBean(CamundaProcessTestContextProxy.class)
        .setContext(camundaProcessTestContext);

    // publish Zeebe client
    testContext
        .getApplicationContext()
        .publishEvent(new CamundaClientCreatedSpringEvent(this, client));
    testContext
        .getApplicationContext()
        .publishEvent(new ZeebeClientCreatedEvent(this, zeebeClient));

    // initialize assertions
    final CamundaDataSource dataSource = new CamundaDataSource(client);
    CamundaAssert.initialize(dataSource);

    // initialize result collector
    processTestResultCollector = new CamundaProcessTestResultCollector(dataSource);

    // deploy resources
    testDeploymentService.deployTestResources(
        testContext.getTestMethod(), testContext.getTestClass(), client);
  }

  @Override
  public void afterTestMethod(final TestContext testContext) throws Exception {
    if (runtime == null) {
      // Skip if the runtime is not created.
      return;
    }
    try {
      processCoverage.collectTestRunCoverage(testContext.getTestMethod().getName());
    } catch (final Throwable t) {
      LOG.warn("Failed to collect test process coverage, skipping.", t);
    }
    if (isTestFailed(testContext)) {
      printTestResults();
    }
    // reset assertions
    CamundaAssert.reset();
    // close Zeebe clients
    testContext
        .getApplicationContext()
        .publishEvent(new CamundaClientClosingSpringEvent(this, client));
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

    // final steps: reset the time and delete data
    // It's important that the runtime clock is reset before the purge is started, as doing it
    // the other way around leads to race conditions and inconsistencies in the tests
    resetRuntimeClock();
    deleteRuntimeData();
  }

  @Override
  public void afterTestClass(final TestContext testContext) throws Exception {
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

  private void initializeJsonMapper(
      final JsonMapper jsonMapper, final io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper) {

    if (jsonMapper != null) {
      CamundaAssert.setJsonMapper(jsonMapper);
    } else if (zeebeJsonMapper != null) {
      CamundaAssert.setJsonMapper(zeebeJsonMapper);
    }
  }

  private CamundaManagementClient createManagementClient(
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    if (runtimeConfiguration.isMultiTenancyEnabled()) {
      return CamundaManagementClient.createAuthenticatedClient(
          runtime.getCamundaMonitoringApiAddress(),
          runtime.getCamundaRestApiAddress(),
          MultiTenancyConfiguration.getBasicAuthCredentials());
    } else {
      return CamundaManagementClient.createClient(
          runtime.getCamundaMonitoringApiAddress(), runtime.getCamundaRestApiAddress());
    }
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

  private void closeCreatedClients() {
    for (final AutoCloseable client : createdClients) {
      try {
        client.close();
      } catch (final Exception e) {
        LOG.debug("Failed to close client, continue.", e);
      }
    }
  }

  private CamundaProcessTestRuntime buildRuntime(
      final TestContext testContext,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    final CamundaClientProperties clientProperties =
        testContext.getApplicationContext().getBean(CamundaClientProperties.class);

    return CamundaSpringProcessTestRuntimeBuilder.buildRuntime(
        containerRuntimeBuilder, runtimeConfiguration, clientProperties);
  }

  private static CamundaClient createClient(
      final CamundaProcessTestContext camundaProcessTestContext) {
    return camundaProcessTestContext.createClient();
  }

  private static ZeebeClient createZeebeClient(
      final CamundaProcessTestContext camundaProcessTestContext) {
    return camundaProcessTestContext.createZeebeClient();
  }

  private static boolean isTestFailed(final TestContext testContext) {
    return testContext.getTestException() != null;
  }

  @Override
  public int getOrder() {
    return Integer.MAX_VALUE;
  }
}
