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
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the Worker component. Verifies that the worker can connect to a Camunda
 * cluster, subscribe to jobs via {@code @JobWorker}, and complete them.
 *
 * <p>Unlike the Starter, the Worker does not block Spring Boot startup. The {@code @JobWorker}
 * annotation registers the job handler automatically, so we can deploy a process and create
 * instances in the test method and verify the worker completes the jobs.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.worker.completion-delay=0ms",
      "load-tester.worker.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
    })
@ActiveProfiles({"worker", "it"})
class WorkerIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private ZeebeClient client;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldCompleteJobs() {
    // given - deploy a process with a service task of type "benchmark-task"
    client.newDeployResourceCommand().addResourceFromClasspath("bpmn/one_task.bpmn").send().join();

    // when - create a process instance
    final var instanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("benchmark")
            .latestVersion()
            .variables("{\"businessKey\": 1}")
            .send()
            .join();

    final long processInstanceKey = instanceEvent.getProcessInstanceKey();

    // then - the @JobWorker in Worker completes the job; verify via process instance query
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var page =
                  client
                      .newProcessInstanceQuery()
                      .filter(f -> f.bpmnProcessId("benchmark").completed(true))
                      .send()
                      .join();
              assertThat(page.items())
                  .describedAs(
                      "Process instance %d should be completed by the worker", processInstanceKey)
                  .anySatisfy(pi -> assertThat(pi.getKey()).isEqualTo(processInstanceKey));
            });
  }
}
