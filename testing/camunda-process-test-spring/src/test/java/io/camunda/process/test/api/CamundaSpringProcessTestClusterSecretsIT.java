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
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {CamundaSpringProcessTestClusterSecretsIT.class},
    properties = {"camunda.process-test.cluster-secrets.MY_API_KEY=test-key"})
@CamundaSpringProcessTest
public class CamundaSpringProcessTestClusterSecretsIT {

  private static final String PROCESS_ID = "cluster-secrets-process";
  private static final String JOB_TYPE = "cluster-secrets-job";
  private static final String SECRET_KEY = "MY_API_KEY";
  private static final String SECRET_VALUE = "test-key";
  private static final String SECRET_VARIABLE_NAME = "apiKey";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              task ->
                  task.zeebeJobType(JOB_TYPE)
                      .zeebeInputExpression("=camunda.secrets." + SECRET_KEY, SECRET_VARIABLE_NAME))
          .endEvent()
          .done();

  @Autowired private CamundaClient client;

  @Autowired private CamundaProcessTestContext processTestContext;

  @Test
  void shouldResolveClusterSecretReference() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(PROCESS, "cluster-secrets-process.bpmn")
        .send()
        .join();

    final ProcessInstanceEvent processInstanceEvent =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    // when
    final AtomicReference<Map<String, Object>> jobVariables = new AtomicReference<>();

    try (final JobWorker ignored =
        client
            .newWorker()
            .jobType(JOB_TYPE)
            .handler(
                (jobClient, job) -> {
                  jobVariables.set(job.getVariablesAsMap());
                  jobClient.newCompleteCommand(job).send().join();
                })
            .open()) {

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).isCompleted();

      assertThat(jobVariables.get()).containsEntry(SECRET_VARIABLE_NAME, SECRET_VALUE);
    }
  }

  @Test
  void shouldResolveClusterSecretForMockJobWorker() {
    // given
    final JobWorkerMock mockedJobWorker = processTestContext.mockJobWorker(JOB_TYPE).thenComplete();

    client
        .newDeployResourceCommand()
        .addProcessModel(PROCESS, "mocked-cluster-secrets-process.bpmn")
        .send()
        .join();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance).isCompleted();

    assertThat(mockedJobWorker.getActivatedJobs().get(0).getVariablesAsMap())
        .containsEntry(SECRET_VARIABLE_NAME, SECRET_VALUE);
  }
}
