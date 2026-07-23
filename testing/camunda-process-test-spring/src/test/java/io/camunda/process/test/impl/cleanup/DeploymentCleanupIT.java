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
package io.camunda.process.test.impl.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * This integration test verifies deployment collection for resource deletion after a test case
 * cleanup in the Spring listener setup.
 */
@SpringBootTest(classes = {DeploymentCleanupIT.TestConfig.class})
@CamundaSpringProcessTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeploymentCleanupIT {

  private static final String MOCK_PROCESS_ID = "dummyProcess";

  private static final TestCleanupStrategy TEST_CLEANUP_STRATEGY = new TestCleanupStrategy();

  @Autowired private CamundaProcessTestContext processTestContext;

  @TestDeployment(resources = {"deploymentCleanupIT/process-3.bpmn"})
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
        .hasSize(4)
        .describedAs("Expected processes from @Deployment annotation")
        .contains("process-1", "process-2")
        .describedAs("Expected processes from @TestDeployment annotation")
        .contains("process-3")
        .describedAs("Expected processes from processTestContext.mockChildProcess()")
        .contains(MOCK_PROCESS_ID);
  }

  @Deployment(
      resources = {"deploymentCleanupIT/process-1.bpmn", "deploymentCleanupIT/process-2.bpmn"})
  @Configuration
  static class TestConfig {

    @Bean
    @Primary
    public CleanupStrategy mockCleanupStrategy() {
      return TEST_CLEANUP_STRATEGY;
    }
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
