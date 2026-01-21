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
package io.camunda.process.test;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class MultiEngineTest {

  @ParameterizedTest
  @ValueSource(strings = {"raft-partition", "engine-2"})
  public void testMultiEngineSetup(final String engineName) throws Exception {
    try (final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080/engines/" + engineName))
            .build()) {
      // given
      final BpmnModelInstance process =
          Bpmn.createExecutableProcess("process")
              .startEvent()
              .name("start")
              .zeebeOutputExpression("\"active\"", "status")
              .userTask()
              .name("task")
              .endEvent()
              .name("end")
              .zeebeOutputExpression("\"ok\"", "result")
              .done();

      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when
      final ProcessInstanceEvent processInstance =
          client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
    }
  }
}
