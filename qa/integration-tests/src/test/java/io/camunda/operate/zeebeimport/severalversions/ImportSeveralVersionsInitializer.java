/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.severalversions;

import static io.camunda.operate.qa.util.migration.AbstractTestFixture.PROPERTIES_PREFIX;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.util.ZeebeVersionsUtil.VERSIONS_DELIMITER;
import static io.camunda.operate.util.ZeebeVersionsUtil.ZEEBE_VERSIONS_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.util.TestContainerUtil;
import io.camunda.operate.util.ZeebeVersionsUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class ImportSeveralVersionsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String OPERATE_PREFIX = "several-versions-operate";
  public static final String ZEEBE_PREFIX = "several-versions-zeebe";

  private static final String BPMN_PROCESS_ID = "demoProcess";
  private static final String JOB_TYPE = "task1";

  private File tmpFolder;

  private ZeebeContainer zeebeContainer;

  private Random random = new Random();

  private ZeebeClient client;

  private int wiCount;
  private int finishedCount;
  private int incidentCount;
  private String processId;

  public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

    tmpFolder = createTemporaryFolder();

    generateDataForAllVersions();

    //prepare Operate configuration
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
        getOperateProperties(zeebeContainer.getExternalGatewayAddress()));
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
        "test.wiCount=" + wiCount,
        "test.finishedCount=" + finishedCount,
        "test.incidentCount=" + incidentCount);

    closeClient();
    TestContainerUtil.stopZeebe(zeebeContainer, tmpFolder);
  }

  private String[] getOperateProperties(final String gatewayAddress) {
    return new String[] {
        PROPERTIES_PREFIX + ".zeebeElasticsearch.prefix=" + ImportSeveralVersionsInitializer.ZEEBE_PREFIX,
        PROPERTIES_PREFIX + ".zeebe.gatewayAddress=" + gatewayAddress,
        PROPERTIES_PREFIX + ".importer.startLoadingDataOnStartup=false"
    };
  }

  private File createTemporaryFolder() {
    File createdFolder;
    try {
      createdFolder = File.createTempFile("junit", "", null);
      createdFolder.delete();
      createdFolder.mkdir();
      return createdFolder;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void generateDataForAllVersions() {
    //read list of supported zeebeVersions
    String[] zeebeVersions = ZeebeVersionsUtil.readProperty(ZEEBE_VERSIONS_PROPERTY_NAME)
        .split(VERSIONS_DELIMITER);

    for (String version : zeebeVersions) {
      closeClient();
      TestContainerUtil.stopZeebe(zeebeContainer, tmpFolder);
      zeebeContainer = TestContainerUtil.startZeebe(tmpFolder.getPath(), version, ZEEBE_PREFIX, 1);
      client = ZeebeClient.newClientBuilder()
          .gatewayAddress(zeebeContainer.getExternalGatewayAddress()).usePlaintext().build();
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
    ElasticsearchUtil.flushData(TestContainerUtil.getEsClient());
    while (exported < wiCount && attempts < 10) {
      sleepFor(1000);
      try {
        exported = ElasticsearchUtil.getFieldCardinality(TestContainerUtil.getEsClient(),
            getZeebeAliasName("process-instance"), "value.processInstanceKey");
      } catch (IOException e) {
        fail("Unable to check for exported data", e);
      } catch (ElasticsearchStatusException ex) {
        //try again
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
        .serviceTask("task1").zeebeJobType(JOB_TYPE)
        .endEvent()
        .done();
  }

  private String getZeebeAliasName(String name) {
    return String.format(ZEEBE_PREFIX + "-" + name);
  }

}
