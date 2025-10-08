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
import io.camunda.client.annotation.Deployment;
import io.camunda.client.api.search.response.ProcessDefinition;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {SpringTestDeploymentIT.TestApplication.class})
@CamundaSpringProcessTest
public class SpringTestDeploymentIT {

  @Autowired private CamundaClient client;

  @TestDeployment(resources = {"connector-outbound-process.bpmn"})
  @Test
  void shouldDeployProcessDefinitions() {
    // given: processes are deployed

    // then
    Awaitility.await("until process definitions are available (eventually)")
        .untilAsserted(
            () -> {
              final List<ProcessDefinition> processDefinitions =
                  client.newProcessDefinitionSearchRequest().send().join().items();

              assertThat(processDefinitions)
                  .extracting(ProcessDefinition::getProcessDefinitionId)
                  .describedAs("Expect the process specified in @TestDeployment to be deployed")
                  .contains("outbound-connector-process")
                  .describedAs("Expect the process specified in @Deployment to be deployed")
                  .contains("connector-process")
                  .hasSize(2);
            });
  }

  @Deployment(resources = {"connector-process.bpmn"})
  @SpringBootApplication
  static class TestApplication {}
}
