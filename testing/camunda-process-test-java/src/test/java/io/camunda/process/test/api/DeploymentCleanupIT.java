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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.process.test.impl.cleanup.CleanupStrategy;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.coverage.CoverageCollector;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeploymentCleanupIT {

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentCleanupIT.class);

  private static final String MOCK_PROCESS_ID = "dummyProcess";

  private static final TestCleanupStrategy TEST_CLEANUP_STRATEGY = new TestCleanupStrategy();

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension(
          new CamundaProcessTestRuntimeBuilder()
              .withCleanupStrategyFactory(dataDeletionMode -> TEST_CLEANUP_STRATEGY),
          CoverageCollector.newBuilder(),
          LOG::info);

  // to be injected
  private CamundaProcessTestContext processTestContext;

  @TestDeployment(
      resources = {"deploymentCleanupIT/process-1.bpmn", "deploymentCleanupIT/process-2.bpmn"})
  @Test
  @Order(1)
  void dummyTestCase() {
    // This test case runs first and deploys processes.
    processTestContext.mockChildProcess(MOCK_PROCESS_ID);
  }

  @Test
  @Order(2)
  void shouldCleanupDeployments() {
    // This test case runs after and verifies that the deployments from the previous test case were
    // cleaned up.
    assertThat(TEST_CLEANUP_STRATEGY.deployments)
        .flatExtracting(DeploymentEvent::getProcesses)
        .extracting(Process::getBpmnProcessId)
        .hasSize(3)
        .describedAs("Expected processes from @TestDeployment annotation")
        .contains("process-1", "process-2")
        .describedAs("Expected processes from processTestContext.mockChildProcess()")
        .contains(MOCK_PROCESS_ID);
  }

  static class TestCleanupStrategy implements CleanupStrategy {

    final Collection<DeploymentEvent> deployments = new ArrayList<>();

    @Override
    public void cleanup(
        final CamundaManagementClient managementClient,
        final Supplier<CamundaClient> clientSupplier,
        final Instant testCaseStartTime,
        final Collection<DeploymentEvent> deployments) {
      this.deployments.addAll(deployments);
    }
  }
}
