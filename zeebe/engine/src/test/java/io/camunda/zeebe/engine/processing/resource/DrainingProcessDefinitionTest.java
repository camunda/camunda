/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationHelper;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Verifies that no new process instances can be created for a process definition that is in the
 * {@link io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState#DRAINING}
 * state, and that a draining definition is finalized once its last instance completes.
 */
public class DrainingProcessDefinitionTest {

  private static final String JOB_TYPE = "task";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().withSearchClientsProxy(searchClientsProxy);

  @Before
  public void setUp() {
    // the finalize-time history deletion creates a DELETE_PROCESS_INSTANCE batch operation, whose
    // execution queries secondary storage; return no items so it completes cleanly rather than
    // fail-looping in tests that only assert on the created batch operation
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessInstanceEntity>().items(List.of()).build());
  }

  @Test
  public void shouldRejectCreateInstanceByProcessIdWhenDraining() {
    // given - a definition kept draining by a still-running instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).create());
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                processId, metadata.getVersion(), metadata.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectCreateInstanceByVersionWhenDraining() {
    // given - a definition kept draining by a still-running instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).create());
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVersion(1).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                processId, metadata.getVersion(), metadata.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectCreateInstanceWithResultWhenDraining() {
    // given - a definition kept draining by a still-running instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).create());
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when
    engine.processInstance().ofBpmnProcessId(processId).withResult().asyncCreate();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT)
            .onlyCommandRejections()
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                processId, metadata.getVersion(), metadata.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectCreateInstanceWithStartInstructionsWhenDraining() {
    // given - a definition kept draining by a still-running instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).create());
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when - bogus element id: proves the draining guard runs before start-instruction validation
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withStartInstruction("nonExistentElement")
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                processId, metadata.getVersion(), metadata.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithLatestBinding() {
    // given - a child definition kept draining by a still-running child instance
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = deployWithJob(childId);
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(childId).create());
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity(
                    "call",
                    c -> c.zeebeProcessId(childId).zeebeBindingType(ZeebeBindingType.latest))
                .endEvent()
                .done())
        .deploy();
    drainViaDeletion(child.getProcessDefinitionKey());

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithDeploymentBinding() {
    // given - deployment binding resolves the called process within the same deployment
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                "child.bpmn",
                Bpmn.createExecutableProcess(childId)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done())
            .withXmlResource(
                "parent.bpmn",
                Bpmn.createExecutableProcess(parentId)
                    .startEvent()
                    .callActivity(
                        "call",
                        c ->
                            c.zeebeProcessId(childId).zeebeBindingType(ZeebeBindingType.deployment))
                    .endEvent()
                    .done())
            .deploy();
    final var child =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(childId))
            .findFirst()
            .orElseThrow();
    // a running child instance keeps the child definition draining rather than fully deleted
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(childId).create());
    drainViaDeletion(child.getProcessDefinitionKey());

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithVersionTagBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(childId)
                    .versionTag("v1")
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity(
                    "call",
                    c ->
                        c.zeebeProcessId(childId)
                            .zeebeBindingType(ZeebeBindingType.versionTag)
                            .zeebeVersionTag("v1"))
                .endEvent()
                .done())
        .deploy();
    // a running child instance keeps the child definition draining rather than fully deleted
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(childId).create());
    drainViaDeletion(child.getProcessDefinitionKey());

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, child);
  }

  private void assertCalledElementIncident(
      final long parentInstanceKey, final ProcessMetadataValue child) {
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(parentInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.CALLED_ELEMENT_ERROR);
    assertThat(incident.getValue().getErrorMessage())
        .isEqualTo(
            "Expected to call process with BPMN process id '%s' and version %d (key %d), but it is being deleted."
                .formatted(
                    child.getBpmnProcessId(), child.getVersion(), child.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectMigrationToDrainingTargetProcess() {
    // given
    final var sourceId = helper.getBpmnProcessId() + "-source";
    final var targetId = helper.getBpmnProcessId() + "-target";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(sourceId).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var target = deploy(targetId, "B");

    // a running target instance keeps the target definition draining rather than fully deleted
    final long targetKeeperKey = engine.processInstance().ofBpmnProcessId(targetId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(targetKeeperKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();
    drainViaDeletion(target.getProcessDefinitionKey());
    final long targetProcessDefinitionKey = target.getProcessDefinitionKey();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to migrate process instance to process definition with key '%d' but it is being deleted"
                .formatted(targetProcessDefinitionKey));
  }

  @Test
  public void shouldNotSpawnInstanceForDrainingDefinitionOnTimerStartEvent() {
    // given - a repeating timer start event so the trigger also reschedules
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithStartEvent(processId, start -> start.timerWithCycle("R2/PT1S"));
    injectDraining(metadata);

    // when - the timer start event fires
    engine.increaseTime(Duration.ofHours(1));
    final var triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .getFirst();

    // then - no instance is spawned and no phantom process instance key leaks into the TRIGGERED
    // event
    assertNoInstanceSpawned(metadata.getProcessDefinitionKey());
    assertThat(triggered.getValue().getProcessInstanceKey())
        .describedAs("TRIGGERED timer of a draining definition carries no phantom instance key")
        .isEqualTo(-1L);
  }

  @Test
  public void shouldNotSpawnInstanceForDrainingDefinitionOnMessageStartEvent() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithStartEvent(processId, start -> start.message("start-message"));
    injectDraining(metadata);

    // when - a message is published against the (still-subscribed) message start event
    engine.message().withName("start-message").withCorrelationKey("key").publish();

    // then - the message is not correlated to a phantom instance and no instance is spawned
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.messageStartEventSubscriptionRecords(
                            MessageStartEventSubscriptionIntent.CORRELATED)
                        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
                        .exists()))
        .describedAs("message is not correlated to a draining definition's start subscription")
        .isFalse();
    assertNoInstanceSpawned(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldNotSpawnInstanceForDrainingDefinitionOnSignalStartEvent() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithStartEvent(processId, start -> start.signal("start-signal"));
    injectDraining(metadata);

    // when - a signal is broadcast to the (still-subscribed) signal start event
    engine.signal().withSignalName("start-signal").broadcast();
    RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
        .withSignalName("start-signal")
        .await();

    // then
    assertNoInstanceSpawned(metadata.getProcessDefinitionKey());
  }

  private ProcessMetadataValue deployWithStartEvent(
      final String processId, final Consumer<StartEventBuilder> startEventConfigurer) {
    final var start = Bpmn.createExecutableProcess(processId).startEvent("start");
    startEventConfigurer.accept(start);
    return engine
        .deployment()
        .withXmlResource(start.endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private void assertNoInstanceSpawned(final long processDefinitionKey) {
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_ACTIVATING)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .exists()))
        .describedAs("no new instance is spawned on a draining definition via a start event")
        .isFalse();
  }

  @Test
  public void shouldRejectActivateElementCommandForDrainingDefinition() {
    // given - a draining definition and a raw ACTIVATE_ELEMENT command for a fresh root process
    // instance, mimicking a follow-up command that outlived the DRAINING mark. This bypasses the
    // EventHandle guard to exercise the defensive ProcessInstanceStateTransitionGuard directly.
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, null);
    injectDraining(metadata);

    final long processInstanceKey = 123L;
    final var record =
        new ProcessInstanceRecord()
            .setBpmnProcessId(processId)
            .setProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .setVersion(metadata.getVersion())
            .setProcessInstanceKey(processInstanceKey)
            .setElementId(processId)
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setTenantId(metadata.getTenantId());

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, record));

    // then - the command is rejected and no instance is activated
    final var rejection =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .onlyCommandRejections()
            .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                processId, metadata.getVersion(), metadata.getProcessDefinitionKey()));
    assertNoInstanceSpawned(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldReportDrainedWhenLastDrainingInstanceCompletes() {
    // given - a draining definition with a single active instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when - the last active instance completes
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then - the definition is removed locally (DELETED) and this partition reports drained
    assertDeletedLocally(metadata.getProcessDefinitionKey());
    assertReportedDrained(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldReportDrainedWhenLastDrainingInstanceTerminates() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when - the last active instance is terminated
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - the definition is removed locally and this partition reports drained
    assertDeletedLocally(metadata.getProcessDefinitionKey());
    assertReportedDrained(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldKeepDrainingWhileOtherInstancesStillRunning() {
    // given - a draining definition with two active instances
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long firstInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long secondInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(firstInstanceKey);
    awaitJobCreated(secondInstanceKey);
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // when - only the first instance completes
    engine.job().ofInstance(firstInstanceKey).withType(JOB_TYPE).complete();

    // then - still draining: a new instance is still rejected, proving it is not yet drained
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);

    // when - the last instance completes
    engine.job().ofInstance(secondInstanceKey).withType(JOB_TYPE).complete();

    // then - this partition reports drained only after the last instance completes
    assertReportedDrained(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldReportDrainedWhenLastDrainingCallActivityChildCompletes() {
    // given - a draining child definition kept alive by a running call-activity child instance
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = deployWithJob(childId);
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childId))
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId(parentId).create();
    final long childInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(childId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    awaitJobCreated(childInstanceKey);
    drainViaDeletion(child.getProcessDefinitionKey());

    // when - the child instance completes
    engine.job().ofInstance(childInstanceKey).withType(JOB_TYPE).complete();

    // then - the draining child definition is reported drained (finalize fires for child processes
    // too)
    assertReportedDrained(child.getProcessDefinitionKey());
  }

  @Test
  public void shouldFullyDeleteWhenLastInstanceDrains() {
    // given - a draining definition on the deployment partition. On this single-partition harness
    // the real deletion auto-seeds only the local partition (1); a second injected DRAINING event
    // carries partitions 2 and 3 so ProcessDrainingApplier seeds them too, simulating the rest of a
    // three-partition cluster.
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drainViaDeletion(processDefinitionKey);
    injectDraining(metadata, false, 2, 3);

    // when - the last active instance completes, finalizing locally and reporting drained
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // when - each partition reports it has finished draining (as the deployment partition receives
    // them: a locally-keyed report for partition 1 and forwarded reports for partitions 2 and 3)
    engine.writeRecords(
        drainReport(processDefinitionKey, metadata, 1),
        drainReport(processDefinitionKey, metadata, 2),
        drainReport(processDefinitionKey, metadata, 3));

    // then - each report clears its reporting partition (DELETE_COMPLETED) and, once the last one
    // arrives, the definition is reported gone cluster-wide exactly once (FULLY_DELETED)
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
                .limit(1)
                .count())
        .describedAs("the definition is reported fully deleted exactly once")
        .isEqualTo(1);
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.DELETE_COMPLETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(3)
                .count())
        .describedAs("each of the three reporting partitions is cleared")
        .isEqualTo(3);
  }

  @Test
  public void shouldMintNewVersionWhenRedeployingIdenticalResourceWhileDraining() {
    // given - a definition kept draining by a still-running instance.
    final var processId = helper.getBpmnProcessId();
    final var resource =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final var v1 =
        engine
            .deployment()
            .withXmlResource(resource)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).create());
    drainViaDeletion(v1.getProcessDefinitionKey());

    // when - the identical resource is redeployed while v1 is still draining
    final var redeployed =
        engine
            .deployment()
            .withXmlResource(resource)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    // then - it is not deduplicated onto the draining v1 (which would make the redeploy vanish once
    // the drain finalizes); a fresh version is minted with a new key
    assertThat(redeployed.isDuplicate())
        .describedAs("a draining definition must not be reused as a deployment duplicate")
        .isFalse();
    assertThat(redeployed.getVersion()).isEqualTo(2);
    assertThat(redeployed.getProcessDefinitionKey()).isNotEqualTo(v1.getProcessDefinitionKey());
    // and - the resource is byte-identical to v1
    assertThat(redeployed.getChecksum())
        .describedAs("the redeployed resource must be byte-identical to the draining v1")
        .isEqualTo(v1.getChecksum());
  }

  @Test
  public void shouldFinalizeIgnoringBannedInstances() {
    // given - a draining definition with two active instances
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long bannedInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long completingInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(bannedInstanceKey);
    awaitJobCreated(completingInstanceKey);
    drainViaDeletion(metadata.getProcessDefinitionKey());

    // and - one instance is banned. A banned instance never completes/terminates and is excluded
    // from the active-instance check, so it must not block finalization.
    final int partitionId = Protocol.decodePartitionId(bannedInstanceKey);
    engine.banInstanceInNewTransaction(partitionId, bannedInstanceKey);

    // when - the other (non-banned) instance completes and triggers the finalize hook
    engine.job().ofInstance(completingInstanceKey).withType(JOB_TYPE).complete();

    // then - the definition is finalized even though a banned instance still references it
    assertDeletedLocally(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldFinalizeDrainingWhenLastInstanceMigratedAway() {
    // given - a draining source definition whose only active instance is about to migrate away, and
    // a separate target definition to receive it
    final var sourceId = helper.getBpmnProcessId() + "-source";
    final var targetId = helper.getBpmnProcessId() + "-target";
    final var source = deployWithJob(sourceId);
    final var target = deployWithJob(targetId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    awaitJobCreated(processInstanceKey);
    drainViaDeletion(source.getProcessDefinitionKey());

    // when - the instance migrates to the target, emptying the draining source without ever
    // emitting
    // a completion or termination event against it
    migrateToTarget(processInstanceKey, target.getProcessDefinitionKey(), "task");

    // then - the source is finalized (physically deleted locally and reported drained) rather than
    // stranded in DRAINING forever
    assertDeletedLocally(source.getProcessDefinitionKey());
    assertReportedDrained(source.getProcessDefinitionKey());
  }

  @Test
  public void shouldNotFinalizeDrainingWhenOtherInstancesRemainAfterMigration() {
    // given - a draining source definition with two active instances, and a target definition
    final var sourceId = helper.getBpmnProcessId() + "-source";
    final var targetId = helper.getBpmnProcessId() + "-target";
    final var source = deployWithJob(sourceId);
    final var target = deployWithJob(targetId);
    final long migratingInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(sourceId).create());
    awaitJobCreated(migratingInstanceKey);
    drainViaDeletion(source.getProcessDefinitionKey());

    // when - only one of the two instances migrates away
    migrateToTarget(migratingInstanceKey, target.getProcessDefinitionKey(), "task");

    // then - the source is not finalized while the other instance still references it: it stays
    // DRAINING, so a new instance is still rejected for that reason (not "not found")
    engine.processInstance().ofBpmnProcessId(sourceId).expectRejection().create();
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_DRAINING.formatted(
                sourceId, source.getVersion(), source.getProcessDefinitionKey()));
  }

  @Test
  public void shouldReportDrainedAcrossPartitionsAfterMigration() {
    // given - a draining source (seeded across three partitions) with one active instance, and a
    // target definition to migrate into
    final var sourceId = helper.getBpmnProcessId() + "-source";
    final var targetId = helper.getBpmnProcessId() + "-target";
    final var source = deployWithJob(sourceId);
    final var target = deployWithJob(targetId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    awaitJobCreated(processInstanceKey);
    drainViaDeletion(source.getProcessDefinitionKey());
    injectDraining(source, false, 2, 3);

    // when - the last active instance migrates away, finalizing partition 1 locally, and each
    // partition then reports it has finished draining
    migrateToTarget(processInstanceKey, target.getProcessDefinitionKey(), "task");
    engine.writeRecords(
        drainReport(source.getProcessDefinitionKey(), source, 1),
        drainReport(source.getProcessDefinitionKey(), source, 2),
        drainReport(source.getProcessDefinitionKey(), source, 3));

    // then - the definition is reported fully deleted cluster-wide exactly once
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(source.getProcessDefinitionKey())
                .limit(1)
                .count())
        .describedAs("the source is reported fully deleted exactly once")
        .isEqualTo(1);
  }

  @Test
  public void shouldResubscribeStartEventsToLatestActiveVersionSkippingDraining() {
    // given - three versions of the same message-start-event definition. Only the latest holds the
    // start-event subscription, so on deletion it must be handed down.
    final var processId = helper.getBpmnProcessId();
    final long v1Key = deployWithMessageStartAndJob(processId, "task-v1").getProcessDefinitionKey();
    final long v2Key = deployWithMessageStartAndJob(processId, "task-v2").getProcessDefinitionKey();
    final long v3Key = deployWithMessageStartAndJob(processId, "task-v3").getProcessDefinitionKey();

    // and - the middle version (v2) is kept DRAINING by a still-running instance, so it cannot take
    // over the subscription
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).withVersion(2).create());
    drainViaDeletion(v2Key);

    // when - the latest ACTIVE version (v3) is deleted
    engine.resourceDeletion().withResourceKey(v3Key).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(v3Key)
        .await();

    // then - the subscription is handed to v1 (the latest ACTIVE version below v3)
    engine.message().withName("start-message").withCorrelationKey("key").publish();
    final var spawned =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(processId)
            .filter(r -> r.getValue().getProcessDefinitionKey() != v2Key)
            .getFirst();
    Assertions.assertThat(spawned.getValue().getProcessDefinitionKey())
        .describedAs("the message start subscription is handed to v1, skipping the DRAINING v2")
        .isEqualTo(v1Key);
  }

  @Test
  public void shouldNotResubscribeStartEventsWhenNoActiveVersionRemainsBelow() {
    // given - two versions of the same message-start-event definition. Only the latest (v2) holds
    // the start-event subscription.
    final var processId = helper.getBpmnProcessId();
    final long v1Key = deployWithMessageStartAndJob(processId, "task-v1").getProcessDefinitionKey();
    final long v2Key = deployWithMessageStartAndJob(processId, "task-v2").getProcessDefinitionKey();

    // and - v1 is kept DRAINING by a still-running instance, so it is not an ACTIVE fallback that
    // could take over the subscription
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).withVersion(1).create());
    drainViaDeletion(v1Key);

    // when - the latest ACTIVE version (v2) is deleted, leaving no ACTIVE version below it
    engine.resourceDeletion().withResourceKey(v2Key).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withProcessDefinitionKey(v2Key)
        .await();

    // then - the subscription is handed to nobody (findLatestActiveVersionBelow returns null), so
    // the start message correlates to no version: v1 is DRAINING and unsubscribed, v2 is gone
    engine.message().withName("start-message").withCorrelationKey("key").publish();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.messageStartEventSubscriptionRecords(
                            MessageStartEventSubscriptionIntent.CORRELATED)
                        .withBpmnProcessId(processId)
                        .exists()))
        .describedAs("the start message correlates to no version - none holds the subscription")
        .isFalse();
  }

  // A none start event (so instances can be created via the API to keep a version DRAINING) plus a
  // message start event (so resubscription is observable by publishing a message). Both branches
  // wait on a job so created instances stay running.
  private ProcessMetadataValue deployWithMessageStartAndJob(
      final String processId, final String taskId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("none-start")
                .serviceTask(taskId, t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .moveToProcess(processId)
                .startEvent("message-start")
                .message("start-message")
                .serviceTask(taskId + "-msg", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  @Test
  public void shouldRejectRepeatedDeletionWhileDrainingWithoutDeletingActiveInstance() {
    // given - a definition kept DRAINING by a still-running instance, deleted with history deletion
    // requested (the only path that would otherwise create a batch operation)
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when - called repeatedly while draining, carrying PROCESS_DEFINITION as the service layer
    // resolves it for a history deletion in production
    for (int i = 0; i < 3; i++) {
      final var rejection =
          engine
              .resourceDeletion()
              .withResourceKey(processDefinitionKey)
              .expectRejection()
              .delete();

      // then - rejected as already-being-deleted (not accepted with a fresh batch): the definition
      // is still draining, so the caller is told to wait rather than getting a misleading not-found
      assertThat(rejection)
          .hasRejectionType(RejectionType.INVALID_STATE)
          .hasRejectionReason(
              "Expected to delete process definition with key `%d`, but it is already being deleted."
                  .formatted(processDefinitionKey));
    }

    // then - no history-deletion batch operation was ever spawned by the repeated calls
    Assertions.assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.batchOperationCreationRecords()
                        .withIntent(BatchOperationIntent.CREATE)
                        .exists()))
        .describedAs("a repeated deletion while draining must not spawn a batch operation")
        .isFalse();

    // and - the still-running instance was not terminated by the repeated deletions
    Assertions.assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_TERMINATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .exists()))
        .describedAs("an active instance must never be deleted prematurely by a repeated deletion")
        .isFalse();

    // and - the definition is not deleted while an instance is still running: it stays draining
    Assertions.assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processRecords()
                        .withIntent(ProcessIntent.DELETED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .exists()))
        .describedAs("the definition must stay draining, not be deleted, while an instance runs")
        .isFalse();
  }

  @Test
  public void shouldPopulateResourceMetadataOnRejectedDeleteWhileDrainingWithoutExplicitType() {
    // given - a definition kept DRAINING by a still-running instance
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    engine.processInstance().ofBpmnProcessId(processId).create();
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when - the repeated delete carries only the resource key, as the default client path does
    final var rejection =
        engine.resourceDeletion().withResourceKey(processDefinitionKey).expectRejection().delete();

    // then - the processor stamps the resolved metadata onto the rejection
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  private RecordToWrite drainReport(
      final long processDefinitionKey,
      final ProcessMetadataValue metadata,
      final int reportingPartitionId) {
    return drainReport(processDefinitionKey, metadata, reportingPartitionId, false);
  }

  private RecordToWrite drainReport(
      final long processDefinitionKey,
      final ProcessMetadataValue metadata,
      final int reportingPartitionId,
      final boolean deleteHistory) {
    // the report key encodes the reporting partition; the in-partition portion is irrelevant, the
    // processor only decodes the partition from it
    final long reportKey =
        Protocol.encodePartitionId(
            reportingPartitionId, Protocol.decodeKeyInPartition(processDefinitionKey));
    return RecordToWrite.command()
        .key(reportKey)
        .process(
            ProcessIntent.DELETE_COMPLETE,
            new ProcessRecord()
                .setKey(processDefinitionKey)
                .setBpmnProcessId(metadata.getBpmnProcessId())
                .setVersion(metadata.getVersion())
                .setResourceName(metadata.getResourceName())
                .setTenantId(metadata.getTenantId()));
  }

  private void assertDeletedLocally(final long processDefinitionKey) {
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .describedAs("the definition is physically removed on this partition (DELETED event)")
        .isTrue();
  }

  private void assertReportedDrained(final long processDefinitionKey) {
    Assertions.assertThat(
            RecordingExporter.processRecords()
                .withRecordType(RecordType.COMMAND)
                .withIntent(ProcessIntent.DELETE_COMPLETE)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .describedAs("this partition reports drained (DELETE_COMPLETE) once its last instance ends")
        .isTrue();
  }

  private void migrateToTarget(
      final long processInstanceKey,
      final long targetProcessDefinitionKey,
      final String elementId) {
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction(elementId, elementId)
        .migrate();
    // await MIGRATED so the source definition's active-instance state has settled before asserting
    RecordingExporter.processInstanceMigrationRecords(ProcessInstanceMigrationIntent.MIGRATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
  }

  private void awaitJobCreated(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();
  }

  private ProcessMetadataValue deployWithJob(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private ProcessMetadataValue deploy(final String processId) {
    return deploy(processId, null);
  }

  private ProcessMetadataValue deploy(final String processId, final String userTaskId) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent();
    if (userTaskId != null) {
      builder.userTask(userTaskId);
    }
    return engine
        .deployment()
        .withXmlResource(builder.endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  /**
   * Deletes the given definition and waits until it is marked {@code DRAINING}. Requires at least
   * one active instance, otherwise the deletion finalizes right away instead of draining.
   */
  private void drainViaDeletion(final long processDefinitionKey) {
    drainViaDeletion(processDefinitionKey, false);
  }

  private void drainViaDeletion(final long processDefinitionKey, final boolean deleteHistory) {
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();
  }

  /**
   * Injects a {@code DRAINING} event directly (applied on the next start via replay), bypassing the
   * deletion processor. Prefer {@link #drainViaDeletion(long)} — this helper is only for states a
   * real deletion cannot reach on this harness:
   *
   * <ul>
   *   <li>start-event race tests, which need a scheduled start event on an already-draining
   *       definition — real deletion unsubscribes start events as it drains;
   *   <li>tests with no active instance to keep the definition draining (e.g. the defensive {@code
   *       ACTIVATE_ELEMENT} guard), where a real deletion would finalize immediately;
   *   <li>deployment-partition aggregation tests that pass {@code drainPartitions} to simulate a
   *       multi-partition cluster on this single partition.
   * </ul>
   *
   * <p>{@code drainPartitions} are the frozen partitions to wait for: {@code
   * ProcessDrainingApplier} seeds one pending deletion per partition on the deployment partition as
   * it applies the event, so this drives the aggregation the same way a real cluster-wide deletion
   * would — no direct state writes needed.
   */
  private void injectDraining(final ProcessMetadataValue metadata) {
    injectDraining(metadata, false);
  }

  private void injectDraining(
      final ProcessMetadataValue metadata,
      final boolean deleteHistory,
      final int... drainPartitions) {
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DRAINING,
                new ProcessRecord()
                    .setKey(metadata.getProcessDefinitionKey())
                    .setBpmnProcessId(metadata.getBpmnProcessId())
                    .setVersion(metadata.getVersion())
                    .setResourceName(metadata.getResourceName())
                    .setTenantId(metadata.getTenantId())
                    .setDrainPartitions(Arrays.stream(drainPartitions).boxed().toList())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
