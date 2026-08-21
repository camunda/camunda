/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretActivationResponseCapture;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the poll path (#B2 of the optimistic-apply-rollback review) does not discard a
 * re-activated agentic job's pending history when the job is optimistically marked as reactivated
 * during {@code JobBatchCollector.collectJobs}, but is later dropped from the batch altogether by
 * {@code JobSecretInjector.injectSecretValues} (called from {@code
 * JobBatchActivateProcessor#activateJobBatch}, after collection). A job dropped this way keeps its
 * old, still-valid lease - discarding its pending items anyway would wipe live configuration the
 * job's next activation is still entitled to. This is a distinct gap from the batch-size rejection
 * inside {@code collectJobs} itself, which was already covered by the "only track as reactivated
 * once actually activated" fix.
 */
public class AgentHistoryDiscardOnJobReactivationSecretDropTest {

  // The default max fragment size of the test log stream, which caps the activation response
  // (see LogStreamBuilderImpl and JobSecretActivationInjectionTest for the same constant).
  private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024;

  @Rule public final EngineRule engine;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SecretActivationResponseCapture secretActivation =
      new SecretActivationResponseCapture();

  public AgentHistoryDiscardOnJobReactivationSecretDropTest() {
    engine =
        EngineRule.singlePartition()
            .withSecretStoreRegistry(
                new SecretStoreRegistry(
                    Map.of("default", new NoopSecretStore()), Map.of("default", secretActivation)));
  }

  @Before
  public void setUp() {
    secretActivation.install(engine.getCommandResponseWriter());
  }

  @Test
  public void
      shouldNotDiscardPendingItemsWhenReactivatedJobIsDroppedBySecretInjectionAfterCollection() {
    // given - an agentic service task whose input mapping references a secret; a small cached
    // value lets the first activation inject and activate normally
    final var jobType = "reactivation-secret-drop";
    secretActivation.putSecret("token", "small-value");
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "agent-task",
                    t ->
                        t.zeebeJobType(jobType)
                            .zeebeAiAgentTaskDefinition()
                            .zeebeInputExpression(
                                "\"Bearer \" + camunda.secrets.token", "authorization"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();
    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("agent-task")
            .getFirst()
            .getKey();

    final var firstBatch =
        engine
            .jobs()
            .withType(jobType)
            .withTimeout(10L)
            .withLease()
            .withRequestStreamId(1)
            .withRequestId(1L)
            .activate();
    final long jobKey = firstBatch.getValue().getJobKeys().get(0);
    final String firstLease = firstBatch.getValue().getJobs().get(0).getLeaseToken();

    // and - a CONFIGURATION item pending under the first lease, not yet committed
    final var configurationItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-1")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));
    final var created =
        engine
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(firstLease)
            .withHistory(List.of(configurationItem))
            .create();
    final long pendingItemKey = created.getValue().getHistory().get(0).getAgentHistoryKey();

    // when - the job times out, then the cached secret value is grown past the max message size,
    // so the reactivation collects and optimistically marks the job as reactivated (minting a new
    // lease), but injectSecretValues drops it from the batch entirely once collection is done
    engine.increaseTime(EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withRecordKey(jobKey).getFirst();
    secretActivation.putSecret("token", "x".repeat(MAX_MESSAGE_SIZE));

    final Record<JobBatchRecordValue> secondBatch =
        engine
            .jobs()
            .withType(jobType)
            .withLease()
            .withRequestStreamId(2)
            .withRequestId(2L)
            .activate();

    // then - the job is not activated: it is dropped for exceeding the message size budget, and
    // gets an incident, exactly like a job whose secret value can never fit any batch
    assertThat(secondBatch.getValue().getJobKeys()).isEmpty();
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).withJobKey(jobKey).getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.MESSAGE_SIZE_EXCEEDED);
    final long clockResetKey = engine.clock().reset().getKey();

    // and - no reactivation discard is emitted for the job: it never actually left its first
    // lease, so a blanket discard would wrongly wipe that lease's still-pending item
    final var agentHistoryRecordsUpToClockReset =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .toList();
    assertThat(agentHistoryRecordsUpToClockReset)
        .noneMatch(
            r ->
                r.getIntent() == AgentHistoryIntent.DISCARD
                    && ((AgentHistoryRecordValue) r.getValue()).getJobKey() == jobKey);
    assertThat(agentHistoryRecordsUpToClockReset)
        .noneMatch(
            r -> r.getIntent() == AgentHistoryIntent.DISCARDED && r.getKey() == pendingItemKey);
  }
}
