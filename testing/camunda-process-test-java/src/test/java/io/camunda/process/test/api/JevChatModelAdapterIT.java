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
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.WeightedExpectation;
import io.camunda.process.test.impl.judge.jev.JevChatModelAdapter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Verifies {@link JevChatModelAdapter} against the real Jev (TypeSafe) API, spinning up a real
 * Camunda engine via {@link CamundaProcessTest}. Everything else in this prototype (the assertion
 * wiring, the weighted-average aggregation, the provider/property resolution) is already covered by
 * mocked unit tests; the one thing those cannot verify is whether the request/response JSON shape
 * assumed in {@link JevChatModelAdapter} — inferred from TypeSafe's public docs, never exercised
 * against a live server — actually matches the real API.
 *
 * <p>Skipped unless a real API key is available. Run manually with:
 *
 * <pre>
 * TYPESAFE_API_KEY=&lt;key&gt; ./mvnw verify -pl testing/camunda-process-test-java \
 *     -Dit.test=JevChatModelAdapterIT -DskipUTs -Dquickly
 * </pre>
 *
 * <p>If this fails on the response side, the fix is localized to {@code
 * JevChatModelAdapter#parseAnswers}.
 */
@CamundaProcessTest
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class JevChatModelAdapterIT {

  // injected by CamundaProcessTest
  private CamundaClient client;

  @AfterEach
  void resetJudgeConfig() {
    CamundaAssert.setJudgeConfig(null);
  }

  @Test
  void shouldEvaluateSingleExpectationAgainstRealJevApi() {
    // given
    CamundaAssert.setJudgeConfig(
        JudgeConfig.of(new JevChatModelAdapter(System.getenv("TYPESAFE_API_KEY"))));

    final ProcessInstanceEvent processInstance = deployAndRunRefundProcess("jev-verification");

    // then - a clearly true criterion should pass
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge("result", "mentions issuing a refund");
  }

  @Test
  void shouldEvaluateWeightedExpectationsAgainstRealJevApi() {
    // given
    CamundaAssert.setJudgeConfig(
        JudgeConfig.of(new JevChatModelAdapter(System.getenv("TYPESAFE_API_KEY"))));

    final ProcessInstanceEvent processInstance =
        deployAndRunRefundProcess("jev-verification-weighted");

    // then - two clearly true criteria, different weights, should pass with a high blended score
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "result",
            Arrays.asList(
                WeightedExpectation.of("is a polite greeting", 1.0),
                WeightedExpectation.of("mentions issuing a refund", 2.0)));
  }

  private ProcessInstanceEvent deployAndRunRefundProcess(final String processId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .zeebeOutputExpression("\"Hello! Here is your refund of $50.\"", "result")
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, processId + ".bpmn").send().join();

    return client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
