/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.it.util.TestHelper.activateAndCompleteJobs;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForAgentInstanceToBeIndexed;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for process instance migration of agent instances, covering both element types
 * {@code AgentInstanceCreateProcessor} supports as an agent instance's owning element: {@code
 * SERVICE_TASK} and {@code AD_HOC_SUB_PROCESS}.
 */
@MultiDbTest
public class AgentInstanceMigrationIT {

  private static final String AGENT_JOB_TYPE = "agent-task";

  private static CamundaClient client;

  @Test
  void shouldMigrateOrphanedButActiveAgentInstanceOfServiceTask() {
    // given — the agentic job completes (and the process moves on to "nextTask") before
    // migration, while the agent instance itself stays active
    final String sourceElementId = "agentTask";
    final String targetElementId = "agentTask2";
    final String sourceNextElementId = "nextTask";
    final String targetNextElementId = "nextTask2";

    final var sourceProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-service-task_v1")
                .startEvent()
                .serviceTask(
                    sourceElementId,
                    t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                .userTask(sourceNextElementId)
                .endEvent()
                .done(),
            "migration-agent-service-task_v1.bpmn");
    final var targetProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-service-task_v2")
                .startEvent()
                .serviceTask(
                    targetElementId,
                    t -> t.zeebeJobType(AGENT_JOB_TYPE).zeebeAiAgentTaskDefinition())
                .userTask(targetNextElementId)
                .endEvent()
                .done(),
            "migration-agent-service-task_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(processInstanceKey, sourceElementId, true);
    final long sourceAgentDefinitionKey =
        client.newAgentInstanceGetRequest(agentInstanceKey).execute().getAgentDefinitionKey();

    activateAndCompleteJobs(client, AGENT_JOB_TYPE, "test-worker", 1);
    waitForElementInstances(
        client, f -> f.elementId(sourceNextElementId).processInstanceKey(processInstanceKey), 1);

    // when — "agentTask" is mapped even though it no longer has an active element instance
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                .addMappingInstruction(sourceElementId, targetElementId)
                .addMappingInstruction(sourceNextElementId, targetNextElementId)
                .build())
        .execute();

    // then — the agent instance is migrated even though its owning element instance already
    // completed and is not part of the migrated element tree
    assertAgentInstanceMigratedTo(
        agentInstanceKey, targetProcess, targetElementId, sourceAgentDefinitionKey);
  }

  @Test
  void shouldMigrateAgentInstanceOfAdHocSubProcess() {
    // given
    final String sourceElementId = "agentAhsp";
    final String targetElementId = "agentAhsp2";

    final var sourceProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-ahsp_v1")
                .startEvent()
                .adHocSubProcess(sourceElementId, p -> p.task("agentTask"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent()
                .done(),
            "migration-agent-ahsp_v1.bpmn");
    final var targetProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-ahsp_v2")
                .startEvent()
                .adHocSubProcess(targetElementId, p -> p.task("agentTask2"))
                .zeebeJobType(AGENT_JOB_TYPE)
                .zeebeAiAgentSubProcessDefinition()
                .endEvent()
                .done(),
            "migration-agent-ahsp_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(processInstanceKey, sourceElementId, false);
    final long sourceAgentDefinitionKey =
        client.newAgentInstanceGetRequest(agentInstanceKey).execute().getAgentDefinitionKey();

    // when
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                .addMappingInstruction(sourceElementId, targetElementId)
                .build())
        .execute();

    // then
    assertAgentInstanceMigratedTo(
        agentInstanceKey, targetProcess, targetElementId, sourceAgentDefinitionKey);
  }

  private long createAgentInstance(
      final long processInstanceKey,
      final String elementId,
      final boolean releaseJobForReactivation) {
    waitForElementInstances(
        client, f -> f.elementId(elementId).processInstanceKey(processInstanceKey), 1);
    final var elementInstanceKey =
        client
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(elementId).processInstanceKey(processInstanceKey))
            .execute()
            .items()
            .getFirst()
            .getElementInstanceKey();

    // CREATE now requires a jobKey backed by an actual activation of the agentic job.
    final var activatedJob =
        client
            .newActivateJobsCommand()
            .jobType(AGENT_JOB_TYPE)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(5))
            .send()
            .join()
            .getJobs()
            .getFirst();

    final var agentInstanceKey =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .jobKey(activatedJob.getKey())
            .jobLease("test-job-lease")
            .history(
                List.of(
                    configurationHistoryItem("gpt-4o", "openai", "You are a helpful assistant.")))
            .execute()
            .getAgentInstanceKey();

    if (releaseJobForReactivation) {
      // Fail it straight back (no backoff) so it stays available for callers that still need
      // to activate/complete it later.
      client.newFailCommand(activatedJob).retries(1).execute();
    }

    waitForAgentInstanceToBeIndexed(client, agentInstanceKey);
    return agentInstanceKey;
  }

  private static AgentInstanceHistoryItem configurationHistoryItem(
      final String model, final String provider, final String systemPrompt) {
    return new AgentInstanceHistoryItem()
        .historyItemId(UUID.randomUUID().toString())
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.CONFIGURATION)
        .content(List.of(AgentInstanceHistoryContent.text("configuration")))
        .producedAt(OffsetDateTime.now())
        .model(model)
        .provider(provider)
        .systemPrompt(List.of(AgentInstanceHistoryContent.text(systemPrompt)));
  }

  private void assertAgentInstanceMigratedTo(
      final long agentInstanceKey,
      final Process targetProcess,
      final String targetElementId,
      final long sourceAgentDefinitionKey) {
    await("agent instance reflects the target process definition after migration")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response = client.newAgentInstanceGetRequest(agentInstanceKey).execute();
              assertSoftly(
                  softly -> {
                    softly
                        .assertThat(response.getProcessDefinitionKey())
                        .as("processDefinitionKey")
                        .isEqualTo(targetProcess.getProcessDefinitionKey());
                    softly
                        .assertThat(response.getProcessDefinitionId())
                        .as("processDefinitionId")
                        .isEqualTo(targetProcess.getBpmnProcessId());
                    softly
                        .assertThat(response.getProcessDefinitionVersion())
                        .as("processDefinitionVersion")
                        .isEqualTo(targetProcess.getVersion());
                    softly
                        .assertThat(response.getElementId())
                        .as("elementId")
                        .isEqualTo(targetElementId);
                    softly
                        .assertThat(response.getAgentDefinitionKey())
                        .as("agentDefinitionKey")
                        .isNotNull()
                        .isPositive()
                        .isNotEqualTo(sourceAgentDefinitionKey);
                  });
            });
  }
}
