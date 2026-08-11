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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for process instance migration of agent instances, covering both element types
 * {@code AgentInstanceCreateProcessor} supports as an agent instance's owning element: {@code
 * SERVICE_TASK} and {@code AD_HOC_SUB_PROCESS}.
 */
@MultiDbTest
public class AgentInstanceMigrationIT {

  private static CamundaClient client;

  @Test
  void shouldMigrateOrphanedButActiveAgentInstanceOfServiceTask() {
    // given — the agentic job completes (and the process moves on to "nextTask") before
    // migration, while the agent instance itself stays active
    final String sourceElementId = "agentTask";
    final String targetElementId = "agentTask2";
    final String sourceNextElementId = "nextTask";
    final String targetNextElementId = "nextTask2";
    final String agentJobType = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

    final var sourceProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-service-task_v1")
                .startEvent()
                .serviceTask(
                    sourceElementId, t -> t.zeebeJobType(agentJobType).zeebeAiAgentTaskDefinition())
                .userTask(sourceNextElementId)
                .endEvent()
                .done(),
            "migration-agent-service-task_v1.bpmn");
    final var targetProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-service-task_v2")
                .startEvent()
                .serviceTask(targetElementId, t -> t.zeebeJobType(agentJobType))
                .userTask(targetNextElementId)
                .endEvent()
                .done(),
            "migration-agent-service-task_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(processInstanceKey, sourceElementId);

    activateAndCompleteJobs(client, agentJobType, "test-worker", 1);
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
    assertAgentInstanceMigratedTo(agentInstanceKey, targetProcess, targetElementId);
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
                .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
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
                .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
                .endEvent()
                .done(),
            "migration-agent-ahsp_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(processInstanceKey, sourceElementId);

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
    assertAgentInstanceMigratedTo(agentInstanceKey, targetProcess, targetElementId);
  }

  @Test
  void shouldRejectMigrationWhenAgentDefinitionTypeChanges() {
    // given — an active AI agent task is mapped onto an external agent task; both are service
    // tasks, so the element type is unchanged and only the agent definition type differs
    final String sourceElementId = "agentTask";
    final String targetElementId = "agentTask2";
    final String agentJobType = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

    final var sourceProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-type-change_v1")
                .startEvent()
                .serviceTask(
                    sourceElementId, t -> t.zeebeJobType(agentJobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done(),
            "migration-agent-type-change_v1.bpmn");
    final var targetProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-type-change_v2")
                .startEvent()
                .serviceTask(
                    targetElementId,
                    t -> t.zeebeJobType(agentJobType).zeebeExternalAgentDefinition())
                .endEvent()
                .done(),
            "migration-agent-type-change_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    // the agent instance's job is left running so its owning element stays active and is part of
    // the migrated element tree that gets validated
    createAgentInstance(processInstanceKey, sourceElementId);

    // when / then — the migration is rejected because the agent definition type changes
    assertThatThrownBy(
            () ->
                client
                    .newMigrateProcessInstanceCommand(processInstanceKey)
                    .migrationPlan(
                        MigrationPlan.newBuilder()
                            .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                            .addMappingInstruction(sourceElementId, targetElementId)
                            .build())
                    .execute())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("different agent definition type");
  }

  @Test
  void shouldRejectMigrationWhenOrphanedAgentInstanceChangesAgentDefinitionType() {
    // given — the agentic job completes (and the process moves on to "nextTask") before migration,
    // orphaning the still-active agent instance; the orphaned instance's element "agentTask" is
    // mapped onto an external agent task, changing the agent definition type for an instance that
    // is no longer part of the migrated element tree
    final String sourceElementId = "agentTask";
    final String targetElementId = "agentTask2";
    final String sourceNextElementId = "nextTask";
    final String targetNextElementId = "nextTask2";
    final String agentJobType = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

    final var sourceProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-orphaned-type-change_v1")
                .startEvent()
                .serviceTask(
                    sourceElementId, t -> t.zeebeJobType(agentJobType).zeebeAiAgentTaskDefinition())
                .userTask(sourceNextElementId)
                .endEvent()
                .done(),
            "migration-agent-orphaned-type-change_v1.bpmn");
    final var targetProcess =
        deployProcessAndWaitForIt(
            client,
            Bpmn.createExecutableProcess("migration-agent-orphaned-type-change_v2")
                .startEvent()
                .serviceTask(
                    targetElementId,
                    t -> t.zeebeJobType(agentJobType).zeebeExternalAgentDefinition())
                .userTask(targetNextElementId)
                .endEvent()
                .done(),
            "migration-agent-orphaned-type-change_v2.bpmn");

    final var processInstanceKey =
        startProcessInstance(client, sourceProcess.getBpmnProcessId()).getProcessInstanceKey();
    createAgentInstance(processInstanceKey, sourceElementId);

    // complete the agentic job so its owning element completes and the agent instance is orphaned
    activateAndCompleteJobs(client, agentJobType, "test-worker", 1);
    waitForElementInstances(
        client, f -> f.elementId(sourceNextElementId).processInstanceKey(processInstanceKey), 1);

    // when / then — the orphaned agent instance is validated too, so the type change is rejected
    assertThatThrownBy(
            () ->
                client
                    .newMigrateProcessInstanceCommand(processInstanceKey)
                    .migrationPlan(
                        MigrationPlan.newBuilder()
                            .withTargetProcessDefinitionKey(targetProcess.getProcessDefinitionKey())
                            .addMappingInstruction(sourceElementId, targetElementId)
                            .addMappingInstruction(sourceNextElementId, targetNextElementId)
                            .build())
                    .execute())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("different agent definition type");
  }

  private long createAgentInstance(final long processInstanceKey, final String elementId) {
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

    final var agentInstanceKey =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("gpt-4o")
            .provider("openai")
            .systemPrompt("You are a helpful assistant.")
            .execute()
            .getAgentInstanceKey();
    waitForAgentInstanceToBeIndexed(client, agentInstanceKey);
    return agentInstanceKey;
  }

  private void assertAgentInstanceMigratedTo(
      final long agentInstanceKey, final Process targetProcess, final String targetElementId) {
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
                  });
            });
  }
}
