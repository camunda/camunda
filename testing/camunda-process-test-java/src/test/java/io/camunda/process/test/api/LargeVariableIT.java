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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class LargeVariableIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldHandleTruncatedVariables() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .endEvent()
            .name("end")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("small", "smallValue");
    variables.put("large", createLargeString(100));

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    CamundaAssert.assertThatProcessInstance(processInstance).isCompleted().hasVariables(variables);
  }

  private String createLargeString(final long sizeKb) {
    final StringBuilder sb = new StringBuilder();
    for (long i = 0; i < sizeKb * 1024; i++) {
      sb.append('a');
    }

    return sb.toString();
  }
}
