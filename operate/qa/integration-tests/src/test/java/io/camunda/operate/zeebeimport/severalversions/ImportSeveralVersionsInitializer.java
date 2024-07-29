/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.severalversions;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.VERSIONS_DELIMITER;
import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_VERSIONS_PROPERTY_NAME;
import static io.camunda.operate.qa.util.TestContainerUtil.PROPERTIES_PREFIX;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class ImportSeveralVersionsInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String OPERATE_PREFIX = "several-versions-operate";
  public static final String ZEEBE_PREFIX = "several-versions-zeebe";

  private static final String BPMN_PROCESS_ID = "demoProcess";
  private static final String JOB_TYPE = "task1";

  private File tmpFolder;

  private ZeebeContainer zeebeContainer;

  private final Random random = new Random();

  private ZeebeClient client;

  private int wiCount;
  private int finishedCount;
  private int incidentCount;
  private String processId;

  @Autowired private TestContainerUtil testContainerUtil;

  @Override
  public void initialize(final ConfigurableApplicationContext configurableApplicationContext) {

    tmpFolder = createTemporaryFolder();

    generateDataForAllVersions();

    // prepare Operate configuration
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
        configurableApplicationContext,
        getOperateProperties(zeebeContainer.getExternalGatewayAddress()));
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
        configurableApplicationContext,
        "test.wiCount=" + wiCount,
        "test.finishedCount=" + finishedCount,
        "test.incidentCount=" + incidentCount);

    closeClient();
    testContainerUtil.stopZeebe(tmpFolder);
  }

  private String[] getOperateProperties(final String gatewayAddress) {
    return new String[] {
      PROPERTIES_PREFIX
          + ".zeebeElasticsearch.prefix="
          + ImportSeveralVersionsInitializer.ZEEBE_PREFIX,
      PROPERTIES_PREFIX + ".zeebe.gatewayAddress=" + gatewayAddress,
      PROPERTIES_PREFIX + ".importer.startLoadingDataOnStartup=false"
    };
  }

  private File createTemporaryFolder() {
    final File createdFolder;
    try {
      createdFolder = File.createTempFile("junit", "", null);
      createdFolder.delete();
      createdFolder.mkdir();
      return createdFolder;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void generateDataForAllVersions() {
    // read list of supported zeebeVersions
    final String[] zeebeVersions =
        ContainerVersionsUtil.readProperty(ZEEBE_VERSIONS_PROPERTY_NAME).split(VERSIONS_DELIMITER);

    for (final String version : zeebeVersions) {
      closeClient();
      testContainerUtil.stopZeebe(tmpFolder);
      zeebeContainer = testContainerUtil.startZeebe(tmpFolder.getPath(), version, ZEEBE_PREFIX, 1);
      client =
          ZeebeClient.newClientBuilder()
              .grpcAddress(URI.create(zeebeContainer.getExternalGatewayAddress()))
              .usePlaintext()
              .build();
      generateDataForCurrentVersion();
    }
  }

  public void closeClient() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  private void generateDataForCurrentVersion() {
    deployProcessWhenNeeded();
    startInstances();
    failInstances();
    finishInstances();
    waitForDataToBeExported();
  }

  private void waitForDataToBeExported() {
    int exported = 0;
    int attempts = 0;
    ElasticsearchUtil.flushData(testContainerUtil.getEsClient());
    while (exported < wiCount && attempts < 10) {
      sleepFor(1000);
      try {
        exported =
            ElasticsearchUtil.getFieldCardinality(
                testContainerUtil.getEsClient(),
                getZeebeAliasName("process-instance"),
                "value.processInstanceKey");
      } catch (final IOException e) {
        fail("Unable to check for exported data", e);
      } catch (final ElasticsearchStatusException ex) {
        // try again
      }
      attempts++;
    }
    if (exported < wiCount) {
      fail("Not all data was exported from Zeebe.");
    }
  }

  private void finishInstances() {
    final int running = wiCount - finishedCount - incidentCount;
    if (running > 1) {
      final int finished = random.nextInt(running - 1);
      ZeebeTestUtil.completeTask(client, JOB_TYPE, "testWorker", null, finished);
      finishedCount += finished;
    }
  }

  private void failInstances() {
    final int running = wiCount - finishedCount - incidentCount;
    if (running > 1) {
      final int failed = random.nextInt(running - 1);
      ZeebeTestUtil.failTask(client, JOB_TYPE, "testWorker", "error", failed);
      incidentCount += failed;
    }
  }

  private void startInstances() {
    for (int i = 0; i < random.nextInt(10) + 1; i++) {
      ZeebeTestUtil.startProcessInstance(client, BPMN_PROCESS_ID, "{\"var\":111}");
      wiCount++;
    }
  }

  private void deployProcessWhenNeeded() {
    if (processId == null) {
      processId = ZeebeTestUtil.deployProcess(client, createModel(), BPMN_PROCESS_ID + ".bpmn");
    }
  }

  private BpmnModelInstance createModel() {
    return Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
        .startEvent("start")
        .serviceTask("task1")
        .zeebeJobType(JOB_TYPE)
        .endEvent()
        .done();
  }

  private String getZeebeAliasName(final String name) {
    return String.format(ZEEBE_PREFIX + "-" + name);
  }
}
