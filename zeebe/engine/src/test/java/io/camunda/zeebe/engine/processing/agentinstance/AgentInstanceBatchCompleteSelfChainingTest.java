/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Kept separate from {@code AgentInstanceCompleteOnProcessInstanceLifecycleTest}, which relies on
 * the default {@code agentInstanceCompletionBatchLimit} (20) — this class configures a small
 * test-only limit so that completing more agent instances than that limit forces {@code
 * AgentInstanceBatchCompleteProcessor} to self-chain multiple {@code
 * AgentInstanceBatchIntent.COMPLETE} cycles, mirroring {@code ExpireMessageSelfChainingTest} for
 * {@code MessageBatch}.
 */
public final class AgentInstanceBatchCompleteSelfChainingTest {

  private static final int BATCH_LIMIT = 2;
  private static final int AGENT_INSTANCE_COUNT = 5;
  private static final String PROCESS_ID = "agent-instance-batch-complete-process";
  private static final String AGENT_TASK_ID = "agent-task";
  private static final String AGENT_JOB_TYPE =
      JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX + "-batch-complete-self-chaining-test";

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(cfg -> cfg.setAgentInstanceCompletionBatchLimit(BATCH_LIMIT));

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldSelfChainAgentInstanceBatchCompleteWhenExceedingBatchLimit() {
    // given — a single process instance with AGENT_INSTANCE_COUNT (5) parallel agentic service
    // task iterations, each carrying its own agent instance, so that completing the process
    // instance must complete more agent instances than BATCH_LIMIT (2) allows per cycle
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    AGENT_TASK_ID,
                    task ->
                        task.zeebeJobType(AGENT_JOB_TYPE)
                            .multiInstance(
                                mi ->
                                    mi.parallel()
                                        .zeebeInputCollectionExpression("=[1,2,3,4,5]")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final List<Long> elementInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(AGENT_TASK_ID)
            .limit(AGENT_INSTANCE_COUNT)
            .map(Record::getKey)
            .toList();
    final List<Long> agentInstanceKeys =
        elementInstanceKeys.stream()
            .map(
                elementInstanceKey ->
                    ENGINE
                        .agentInstances()
                        .withElementInstanceKey(elementInstanceKey)
                        .create()
                        .getKey())
            .toList();

    // when — every agentic job completes, letting the multi-instance body, and in turn the
    // process instance, complete
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(AGENT_JOB_TYPE)
                .limit(AGENT_INSTANCE_COUNT)
                .count())
        .as("all %d agentic jobs are created before they are activated", AGENT_INSTANCE_COUNT)
        .isEqualTo(AGENT_INSTANCE_COUNT);
    final var jobBatch =
        ENGINE
            .jobs()
            .withType(AGENT_JOB_TYPE)
            .withMaxJobsToActivate(AGENT_INSTANCE_COUNT)
            .activate();
    jobBatch.getValue().getJobKeys().forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());

    // then — completing 5 agent instances with a batch limit of 2 must take exactly 3 self-chained
    // cycles: 2, then 2, then the final 1, followed by a single terminal COMPLETED
    assertThat(
            RecordingExporter.agentInstanceBatchRecords(AgentInstanceBatchIntent.COMPLETE)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3)
                .count())
        .as(
            "completing %d agent instances with a batch limit of %d requires exactly 3"
                + " self-chained AGENT_INSTANCE_BATCH:COMPLETE commands (2, 2, then 1)",
            AGENT_INSTANCE_COUNT, BATCH_LIMIT)
        .isEqualTo(3);
    assertThat(
            RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(AGENT_INSTANCE_COUNT)
                .map(Record::getKey))
        .as("every one of the %d agent instances reaches COMPLETED", AGENT_INSTANCE_COUNT)
        .containsExactlyInAnyOrderElementsOf(agentInstanceKeys);
    assertThat(
            RecordingExporter.agentInstanceBatchRecords(AgentInstanceBatchIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .as("a single terminal AGENT_INSTANCE_BATCH:COMPLETED is written once exhausted")
        .isTrue();
  }
}
