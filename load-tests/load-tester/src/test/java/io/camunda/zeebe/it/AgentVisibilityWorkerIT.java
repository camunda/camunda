/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
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
 * End-to-end integration test for the agent-visibility scenario's ad-hoc-sub-process tool-calling
 * mechanism, following the same shape as {@link StarterWorkerIT}: both the {@code starter} and
 * {@code worker} profiles run in one Spring context against a real {@link CamundaContainer}.
 *
 * <p>The {@code worker} profile's {@code camunda.client.worker.defaults.type} is pointed at the
 * ad-hoc-sub-process orchestrator's job type, so the Spring-managed {@link
 * io.camunda.zeebe.worker.Worker} bean drives {@code Worker#handleAdHocSubProcessOrchestration} for
 * every round. The three tool job types have no equivalent scenario worker role in this single-JVM
 * test, so three trivial, immediately-completing job workers are opened directly against the
 * injected {@link CamundaClient} to stand in for them.
 *
 * <p>Deliberately narrow in scope: this only asserts the process instance reaches {@code
 * COMPLETED}, proving the round mechanism survives a real engine (lease handling, {@code JobResult}
 * acceptance, no rejections). The exact per-round/per-tool activation sequence is already covered
 * by the mocked {@code WorkerTest}; {@code AgentInstance}/{@code AgentHistory} assertions are added
 * once that simulation exists, in a later commit.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      // starter: deploy the agent-visibility BPMN and create a handful of instances
      "load-tester.starter.process-id=agentVisibilityBenchmark",
      "load-tester.starter.bpmn-xml-path=bpmn/agent-visibility/agentTools.bpmn",
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=5",
      "load-tester.starter.threads=1",
      // worker: subscribed to the ad-hoc-sub-process orchestrator's job type, no artificial delay
      "camunda.client.worker.defaults.type=agent-visibility-orchestrator",
      "load-tester.worker.completion-delay=0ms",
      // avoid background meters hitting the testcontainer gateway
      "load-tester.monitor-data-availability=false",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "worker", "it"})
class AgentVisibilityWorkerIT {

  private static final String TOOL_LOOKUP_ACCOUNT = "tool-lookup-account";
  private static final String TOOL_CALCULATE_SCORE = "tool-calculate-score";
  private static final String TOOL_SEND_NOTIFICATION = "tool-send-notification";

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private CamundaClient client;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldCompleteProcessInstanceThroughAdHocSubProcessRoundSchedule() {
    // given — the starter has already deployed agentTools.bpmn and created ~5 instances of
    //         "agentVisibilityBenchmark" (CommandLineRunner blocks context startup for
    //         duration-limit=5s). The Spring-managed worker drives the ad-hoc-sub-process
    //         orchestrator's job type; the three tool job types are completed by lightweight
    //         workers opened directly below, standing in for the scenario's other worker roles.
    try (JobWorker lookupAccount = openImmediateCompletionWorker(TOOL_LOOKUP_ACCOUNT);
        JobWorker calculateScore = openImmediateCompletionWorker(TOOL_CALCULATE_SCORE);
        JobWorker sendNotification = openImmediateCompletionWorker(TOOL_SEND_NOTIFICATION)) {

      // then — every instance should progress through all four rounds of the schedule and
      //        reach COMPLETED; Awaitility handles the async gap across rounds.
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(2))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                final var response =
                    client
                        .newProcessInstanceSearchRequest()
                        .filter(f -> f.processDefinitionId("agentVisibilityBenchmark"))
                        .send()
                        .join();

                assertThat(response.items())
                    .describedAs(
                        "Starter should have created instances of 'agentVisibilityBenchmark'")
                    .isNotEmpty();

                assertThat(response.items())
                    .describedAs(
                        "The ad-hoc-sub-process round schedule should have run to completion "
                            + "for at least one instance")
                    .anyMatch(pi -> pi.getState() == ProcessInstanceState.COMPLETED);
              });
    }
  }

  private JobWorker openImmediateCompletionWorker(final String jobType) {
    return client
        .newWorker()
        .jobType(jobType)
        .handler((jobClient, job) -> jobClient.newCompleteCommand(job).send().join())
        .open();
  }
}
