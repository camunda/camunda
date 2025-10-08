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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {SpringTestDeploymentIT.class})
@CamundaSpringProcessTest
public class SpringTestDeploymentIT {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  @TestDeployment(resources = "connector-process.bpmn")
  @Test
  void shouldCreateProcessInstance() {
    // when
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("connector-process")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance).isCreated();
    assertThat(processInstance.getBpmnProcessId()).isEqualTo("connector-process");
    assertThat(processInstance.getProcessInstanceKey()).isGreaterThan(0);
  }

  @TestDeployment(resources = {"connector-process.bpmn", "connector-outbound-process.bpmn"})
  @Test
  void shouldDeployMultipleProcessDefinitions() {
    // when
    final ProcessInstanceEvent processInstance1 =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("connector-process")
            .latestVersion()
            .send()
            .join();

    final ProcessInstanceEvent processInstance2 =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("outbound-connector-process")
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance1).isCreated();
    CamundaAssert.assertThat(processInstance2).isCreated();
    assertThat(processInstance1.getProcessInstanceKey())
        .isNotEqualTo(processInstance2.getProcessInstanceKey());
  }

  @TestDeployment(resources = "connector-process.bpmn")
  @Test
  void shouldVerifyProcessDefinitionExists() {
    // Verify that the deployed process definition is available
    assertThatCode(
            () -> {
              final ProcessInstanceEvent processInstance =
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId("connector-process")
                      .latestVersion()
                      .send()
                      .join();

              assertThat(processInstance).isNotNull();
              assertThat(processInstance.getProcessInstanceKey()).isGreaterThan(0);
              assertThat(processInstance.getBpmnProcessId()).isEqualTo("connector-process");
            })
        .doesNotThrowAnyException();
  }

  @TestDeployment(resources = "connector-process.bpmn")
  @Test
  void shouldVerifySpringContextIntegration() {
    // given
    final CamundaClient contextClient = processTestContext.createClient();

    // then
    assertThatCode(
            () -> {
              final ProcessInstanceEvent processInstance =
                  contextClient
                      .newCreateInstanceCommand()
                      .bpmnProcessId("connector-process")
                      .latestVersion()
                      .send()
                      .join();

              CamundaAssert.assertThat(processInstance).isCreated();
            })
        .doesNotThrowAnyException();
  }
}
