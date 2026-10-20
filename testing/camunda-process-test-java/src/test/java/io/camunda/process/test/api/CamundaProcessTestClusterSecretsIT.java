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
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CamundaProcessTestClusterSecretsIT {

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withClusterSecret("MY_API_KEY", "test-key");

  private CamundaClient client;

  @Test
  void shouldResolveClusterSecretReference() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("cluster-secrets-process")
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType("cluster-secrets-job")
                            .zeebeInputExpression("=camunda.secrets.MY_API_KEY", "apiKey"))
                .endEvent()
                .done(),
            "cluster-secrets-process.bpmn")
        .send()
        .join();

    final AtomicReference<Map<String, Object>> variables = new AtomicReference<>();

    // when
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("cluster-secrets-process")
        .latestVersion()
        .send()
        .join();

    try (final JobWorker ignored =
        client
            .newWorker()
            .jobType("cluster-secrets-job")
            .handler(
                (jobClient, job) -> {
                  variables.set(job.getVariablesAsMap());
                  jobClient.newCompleteCommand(job).send().join();
                })
            .open()) {

      // then
      Awaitility.await("until the job is activated with the resolved secret")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(variables.get()).containsEntry("apiKey", "test-key"));
    }
  }
}
