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
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class TestDeploymentIT {

  private CamundaClient client;

  @TestDeployment(resources = "coverage/test-with-simple-gateway.bpmn")
  @Test
  void shouldVerifyProcessDefinitionExists() {
    assertThatCode(
            () -> {
              final ProcessInstanceEvent processInstance =
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId("test-with-simple-gateway")
                      .latestVersion()
                      .send()
                      .join();

              assertThat(processInstance).isNotNull();
              assertThat(processInstance.getProcessInstanceKey()).isGreaterThan(0);
              assertThat(processInstance.getBpmnProcessId()).isEqualTo("test-with-simple-gateway");
            })
        .doesNotThrowAnyException();
  }

  @TestDeployment(resources = "coverage/test-with-simple-gateway.bpmn")
  @Test
  void shouldDeployProcessDefinition() {
    // when
    final List<ProcessDefinition> defs =
        client
            .newProcessDefinitionSearchRequest()
            .filter((fn) -> fn.processDefinitionId("test-with-simple-gateway"))
            .send()
            .join()
            .items();
    // then
    assertThat(defs).hasSize(1);
  }

  @TestDeployment(
      resources = {
        "coverage/test-with-simple-gateway.bpmn",
        "coverage/test-with-event-based-gateway.bpmn"
      })
  @Test
  void shouldDeployMultipleProcessDefinitions() {
    // given
    // Both processes are deployed

    // when
    final ProcessInstanceEvent processInstance1 =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("test-with-simple-gateway")
            .latestVersion()
            .send()
            .join();

    final ProcessInstanceEvent processInstance2 =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("test-with-event-based-gateway")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance1).isCreated();
    CamundaAssert.assertThat(processInstance2).isCreated();
  }
}
