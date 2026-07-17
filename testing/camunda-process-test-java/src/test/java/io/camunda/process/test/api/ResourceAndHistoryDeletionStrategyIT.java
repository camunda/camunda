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
import io.camunda.process.test.impl.cleanup.ResourceAndHistoryDeletionStrategy;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

@CamundaProcessTest
public class ResourceAndHistoryDeletionStrategyIT {

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withDataDeletionMode(DataDeletionMode.NONE);

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldDeleteTestCaseDataWithoutTouchingPreviousDeployments() {
    // given
    final String preexistingProcessId = "preexisting-" + System.nanoTime();
    deploySimpleProcess(preexistingProcessId);
    client.newCreateInstanceCommand().bpmnProcessId(preexistingProcessId).latestVersion().send().join();

    final Instant testCaseStartTime = processTestContext.getCurrentTime();
    final String testCaseProcessId = "test-case-" + System.nanoTime();
    final long testCaseDeploymentKey = deploySimpleProcess(testCaseProcessId);
    client.newCreateInstanceCommand().bpmnProcessId(testCaseProcessId).latestVersion().send().join();

    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();

    // when
    strategy.cleanup(
        Mockito.mock(CamundaManagementClient.class),
        processTestContext::createClient,
        testCaseStartTime,
        Collections.singleton(testCaseDeploymentKey));

    // then
    Awaitility.await("Wait until test-case process instances are deleted")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newProcessInstanceSearchRequest()
                            .filter(filter -> filter.processDefinitionId(testCaseProcessId))
                            .send()
                            .join()
                            .items())
                    .isEmpty());

    assertThat(
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(preexistingProcessId)
                .latestVersion()
                .send()
                .join())
        .isNotNull();
  }

  private long deploySimpleProcess(final String processId) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn")
        .send()
        .join()
        .getKey();
  }
}
