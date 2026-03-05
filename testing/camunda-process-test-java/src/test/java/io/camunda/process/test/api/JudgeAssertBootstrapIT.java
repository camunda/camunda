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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration test that verifies the {@link
 * io.camunda.process.test.api.judge.JudgeConfigBootstrapProvider} SPI is auto-discovered via
 * ServiceLoader when no explicit {@code withJudgeConfig()} is configured.
 *
 * <p>The {@code FakeJudgeConfigBootstrapProvider} (registered via test-scoped SPI) always returns
 * score 1.0, so any {@code hasVariableSatisfiesJudge} assertion passes without a real LLM.
 */
@CamundaProcessTest
public class JudgeAssertBootstrapIT {

  private CamundaClient client;

  @Test
  void shouldAutoBootstrapJudgeConfigViaSpi() {
    // given - a process that sets a variable and ends
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    final long processDefinitionKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getProcesses()
            .get(0)
            .getProcessDefinitionKey();

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(Collections.singletonMap("result", "The order was processed successfully"))
            .send()
            .join();

    // when / then - judge config is auto-bootstrapped via SPI (FakeJudgeConfigBootstrapProvider)
    Assertions.assertThat(CamundaAssert.getJudgeConfig())
        .as("JudgeConfig should be auto-bootstrapped via SPI")
        .isNotNull();

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge("result", "The order was processed");
  }
}
