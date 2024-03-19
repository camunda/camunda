/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  private Random random = new Random();

  private ZeebeClient client;

  private int wiCount;
  private int finishedCount;
  private int incidentCount;
  private String processId;

  @Autowired private TestContainerUtil testContainerUtil;

  public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void generateDataForAllVersions() {
    // read list of supported zeebeVersions
    final String[] zeebeVersions =
        ContainerVersionsUtil.readProperty(ZEEBE_VERSIONS_PROPERTY_NAME).split(VERSIONS_DELIMITER);

    for (String version : zeebeVersions) {
      closeClient();
      testContainerUtil.stopZeebe(tmpFolder);
      zeebeContainer = testContainerUtil.startZeebe(tmpFolder.getPath(), version, ZEEBE_PREFIX, 1);
      client =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
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
      } catch (IOException e) {
        fail("Unable to check for exported data", e);
      } catch (ElasticsearchStatusException ex) {
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

  private String getZeebeAliasName(String name) {
    return String.format(ZEEBE_PREFIX + "-" + name);
  }
}
