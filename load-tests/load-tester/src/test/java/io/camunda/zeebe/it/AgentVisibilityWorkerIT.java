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
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import java.time.Duration;
import java.util.Comparator;
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
 * <p>The process instance reaching {@code COMPLETED} proves the round mechanism survives a real
 * engine (lease handling, {@code JobResult} acceptance, no rejections); the exact per-round/
 * per-tool activation sequence is already covered by the mocked {@code WorkerTest}, so it is not
 * re-asserted here. Agent-instance simulation is enabled so the resulting {@code AgentInstance}/
 * {@code AgentHistory} records - proving the engine actually accepts the CREATE/UPDATE commands
 * {@code Worker#simulateAgentInstance} issues, and commits their history items - can be asserted
 * for real, not just mocked.
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
      // Job streaming causes the orchestrator's job to occasionally be delivered twice
      // concurrently (a known streaming+poll race); the second delivery's AgentInstance UPDATE
      // is then rejected with NOT_FOUND ("job was not active") since the first delivery already
      // completed it - silently dropping that round's history item and failing the job for a
      // retry. Harmless for the plain-completion path (a second, redundant complete is just
      // ignored), but not for simulateAgentInstance's synchronous, non-idempotent calls. Disabled
      // here; see ctxt/agent-visibility-job-streaming-discovery.md for the full writeup and the
      // open question of how to apply this to the real scenario's orchestrator role.
      "camunda.client.worker.defaults.stream-enabled=false",
      // agent-instance simulation (the "treatment" configuration): CREATE requires the job to be
      // activated with a lease, per CreateAgentInstanceCommandStep1#jobLease's javadoc
      "load-tester.worker.agent-instance-simulation-enabled=true",
      "camunda.client.worker.defaults.with-lease=true",
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

                final var completedProcessInstanceKey =
                    response.items().stream()
                        .filter(pi -> pi.getState() == ProcessInstanceState.COMPLETED)
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new AssertionError(
                                    "The ad-hoc-sub-process round schedule should have run to "
                                        + "completion for at least one instance"))
                        .getProcessInstanceKey();

                // and — an AgentInstance was created for that instance, since the round-1
                // CREATE call (Worker#simulateAgentInstance) is what the engine's
                // zeebe:agentDefinition validation is exercising here
                final var agentInstances =
                    client
                        .newAgentInstanceSearchRequest()
                        .filter(f -> f.processInstanceKey(completedProcessInstanceKey))
                        .send()
                        .join();
                assertThat(agentInstances.items())
                    .describedAs("An AgentInstance should exist for the completed process instance")
                    .isNotEmpty();
                final var agentInstanceKey = agentInstances.items().get(0).getAgentInstanceKey();

                // and — its history holds exactly the 4 items simulateAgentInstance produced:
                // 1 CONFIGURATION (round 1's CREATE) + 3 ASSISTANT (rounds 2-4's UPDATEs),
                // and the engine has committed all of them (JobCompleteProcessor fires
                // AgentHistoryIntent.COMMIT on every completion of a job belonging to an agent)
                final var history =
                    client.newAgentInstanceHistorySearchRequest(agentInstanceKey).send().join();
                final var items =
                    history.items().stream()
                        .sorted(Comparator.comparingInt(item -> item.getLoopIteration()))
                        .toList();

                assertThat(items)
                    .describedAs(
                        "Expected 1 CONFIGURATION item (CREATE) + 3 ASSISTANT items (UPDATE x3)")
                    .hasSize(4);
                assertThat(items.get(0).getRole())
                    .isEqualTo(AgentInstanceHistoryRole.CONFIGURATION);
                assertThat(items.subList(1, 4))
                    .allMatch(item -> item.getRole() == AgentInstanceHistoryRole.ASSISTANT);
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
