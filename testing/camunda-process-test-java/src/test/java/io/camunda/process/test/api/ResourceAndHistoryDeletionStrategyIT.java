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
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.impl.cleanup.ResourceAndHistoryDeletionStrategy;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@CamundaProcessTest
public class ResourceAndHistoryDeletionStrategyIT {

  private static final String BEFORE_EACH_PROCESS = "before-each-process";
  private static final String TEST_CASE_PROCESS = "test-case-process";

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void setup() {
    deployProcess(BEFORE_EACH_PROCESS);
    createProcessInstance(BEFORE_EACH_PROCESS);

    // move time forward to ensure that the test case start time is after the preexisting process
    // instance creation time
    processTestContext.increaseTime(Duration.ofMinutes(1));
  }

  @Test
  void shouldDeleteTestCaseDataWithoutTouchingPreviousDeployments() {
    // given
    final Instant testCaseStartTime = processTestContext.getCurrentTime();

    final DeploymentEvent testCaseDeployment = deployProcess(TEST_CASE_PROCESS);
    createProcessInstance(TEST_CASE_PROCESS);

    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();

    // when
    strategy.cleanup(
        Mockito.mock(CamundaManagementClient.class),
        processTestContext::createClient,
        testCaseStartTime,
        Collections.singleton(testCaseDeployment));

    // then
    Awaitility.await("Wait until test-case process instances are deleted")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(
                      client
                          .newProcessInstanceSearchRequest()
                          .filter(filter -> filter.processDefinitionId(TEST_CASE_PROCESS))
                          .send()
                          .join()
                          .items())
                  .describedAs("Expected test-case process instances to be deleted")
                  .isEmpty();

              assertThat(
                      client
                          .newProcessDefinitionSearchRequest()
                          .filter(filter -> filter.processDefinitionId(TEST_CASE_PROCESS))
                          .send()
                          .join()
                          .items())
                  .describedAs("Expected test-case process definitions to be deleted")
                  .isEmpty();
            });

    assertThat(client.newProcessInstanceSearchRequest().send().join().items())
        .extracting(ProcessInstance::getProcessDefinitionId)
        .describedAs("Expected test-case process instances to be deleted")
        .doesNotContain(TEST_CASE_PROCESS)
        .describedAs("Expected preexisting process instances to remain")
        .contains(BEFORE_EACH_PROCESS);

    assertThat(client.newProcessDefinitionSearchRequest().send().join().items())
        .extracting(ProcessDefinition::getProcessDefinitionId)
        .describedAs("Expected test-case process definitions to be deleted")
        .doesNotContain(TEST_CASE_PROCESS)
        .describedAs("Expected preexisting process definitions to remain")
        .contains(BEFORE_EACH_PROCESS);
  }

  private DeploymentEvent deployProcess(final String processId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("user-task")
            .zeebeUserTask()
            .endEvent()
            .done();

    return client
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();
  }

  private void createProcessInstance(final String processId) {
    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
