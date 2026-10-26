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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessDefinitionSelector;
import io.camunda.process.test.api.testCases.ImmutableProcessInstanceSelector;
import io.camunda.process.test.api.testCases.ImmutableTestCase;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertVariablesInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.assertProcessInstance.ProcessInstanceState;
import io.camunda.process.test.impl.testCases.CamundaTestCaseRunner;
import io.camunda.process.test.impl.testCases.TestRunContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check that run-ID test isolation scopes a JSON test-case run to the instance it
 * started, instead of taking the oldest instance of the process definition.
 *
 * <p>Isolation is enabled by constructing the runner with an enabled {@link TestRunContext} — the
 * explicit, config-driven path a host such as the hub uses — rather than a system property.
 */
@CamundaProcessTest
public class CptRunIsolationIT {

  // injected by the extension
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldScopeAssertionToTheRunsOwnInstance() {
    // given: a long-running process so both instances stay active during the assertion
    final String processId = "isolation-process-" + UUID.randomUUID();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().userTask("task").endEvent().done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // and: a competing instance of the SAME definition started outside the run (older,
    // marker=seed).
    // It is created after the test start time, so the start-time filter does not exclude it — only
    // run-ID scoping can keep the run off it.
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(Collections.singletonMap("marker", "seed"))
        .send()
        .join();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(findInstances(processId)).hasSize(1));

    // when: a test case creates its own instance (marker=run) and asserts marker=run.
    // Oldest-first selection would resolve the seed instance (marker=seed) and fail; the run-ID
    // variable filter resolves the run's instance instead. A broken filter value would match no
    // instance and time out, so a green run also proves the filter encoding.
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("run-isolation")
            .addInstructions(
                ImmutableCreateProcessInstanceInstruction.builder()
                    .processDefinitionSelector(
                        ImmutableProcessDefinitionSelector.builder()
                            .processDefinitionId(processId)
                            .build())
                    .putVariables("marker", "run")
                    .build())
            .addInstructions(
                ImmutableAssertVariablesInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId(processId)
                            .build())
                    .putVariables("marker", "run")
                    .build())
            .build();

    // then
    final CamundaTestCaseRunner runner =
        new CamundaTestCaseRunner(
            processTestContext, TestRunContext.enabled(UUID.randomUUID().toString()));
    assertThatNoException().isThrownBy(() -> runner.run(testCase));

    // and: the run also tagged its instance with the run-ID variable
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(findInstances(processId)).hasSize(2));
  }

  @Test
  void shouldScopeJobCompletionToTheRunsOwnInstance() {
    // given: a process that waits at a service-task job
    final String processId = "job-isolation-" + UUID.randomUUID();
    final String jobType = "work-" + UUID.randomUUID();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task")
            .zeebeJobType(jobType)
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // and: a competing instance (marker=seed) waiting at a job of the same type
    client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(Collections.singletonMap("marker", "seed"))
        .send()
        .join();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(findInstances(processId)).hasSize(1));

    // when: a test case creates its own instance, completes its job (scoped by process definition),
    // then asserts the instance completed. Job search can't filter by variable, so this only works
    // if the run's instance key is resolved and used to scope the job search.
    final TestCase testCase =
        ImmutableTestCase.builder()
            .name("job-isolation")
            .addInstructions(
                ImmutableCreateProcessInstanceInstruction.builder()
                    .processDefinitionSelector(
                        ImmutableProcessDefinitionSelector.builder()
                            .processDefinitionId(processId)
                            .build())
                    .putVariables("marker", "run")
                    .build())
            .addInstructions(
                ImmutableCompleteJobInstruction.builder()
                    // No process definition on the selector — scoping must resolve the run's
                    // instance by the run-ID variable alone (the hub's job selectors look like
                    // this).
                    .jobSelector(
                        ImmutableJobSelector.builder().jobType(jobType).elementId("task").build())
                    .build())
            .addInstructions(
                ImmutableAssertProcessInstanceInstruction.builder()
                    .processInstanceSelector(
                        ImmutableProcessInstanceSelector.builder()
                            .processDefinitionId(processId)
                            .build())
                    .state(ProcessInstanceState.IS_COMPLETED)
                    .build())
            .build();

    // then
    final CamundaTestCaseRunner runner =
        new CamundaTestCaseRunner(
            processTestContext, TestRunContext.enabled(UUID.randomUUID().toString()));
    assertThatNoException().isThrownBy(() -> runner.run(testCase));

    // and: the seed instance was not touched — its job is still open, so it stays active
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(findActiveInstances(processId)).hasSize(1));
  }

  private List<ProcessInstance> findInstances(final String processId) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items();
  }

  private List<ProcessInstance> findActiveInstances(final String processId) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(
            f ->
                f.processDefinitionId(processId)
                    .state(io.camunda.client.api.search.enums.ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .items();
  }
}
