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
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.NestedRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Verifies that no new process instances can be created for a process definition that is in the
 * {@link io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState#DRAINING}
 * state.
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
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);

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
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);

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
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);

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
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);

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
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = deploy(childId);
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
    drain(child);

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithDeploymentBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    // deployment binding resolves the called process within the same deployment
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                "child.bpmn", Bpmn.createExecutableProcess(childId).startEvent().endEvent().done())
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
    drain(child);

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
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
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
    drain(child);

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

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();
    drain(target);
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
    drain(metadata);

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
    drain(metadata);

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
    drain(metadata);

    // when - a signal is broadcast to the (still-subscribed) signal start event
    engine.signal().withSignalName("start-signal").broadcast();
    RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
        .withSignalName("start-signal")
        .await();

    // then
    assertNoInstanceSpawned(metadata.getProcessDefinitionKey());
  }

  @Test
  public void shouldNotSpawnInstanceForDrainingDefinitionOnConditionalStartEvent() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata =
        deployWithStartEvent(processId, start -> start.condition(c -> c.condition("=x > y")));
    drain(metadata);

    // when - the conditional start event's condition is evaluated to true
    engine.conditionalEvaluation().withVariables(Map.of("x", 100, "y", 1)).evaluate();

    // then - the guard blocks the activation and the draining definition is not reported as started
    final var evaluated =
        RecordingExporter.conditionalEvaluationRecords(ConditionalEvaluationIntent.EVALUATED)
            .getFirst();
    assertThat(evaluated.getValue().getStartedProcessInstances())
        .describedAs("a draining definition is not reported as a started instance")
        .isEmpty();
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
        .get(0);
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
    final var metadata = deploy(processId);
    drain(metadata);

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
    drain(metadata);

    // when - the last active instance completes
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then - this partition physically removes the definition locally (DELETING/DELETED) and then
    // reports it has finished draining (DELETE_COMPLETE) so ProcessDeleteCompleteProcessor can
    // aggregate the per-partition reports cluster-wide.
    assertDeletedLocally(metadata.getProcessDefinitionKey());
    assertThat(
            RecordingExporter.processRecords()
                .withRecordType(RecordType.COMMAND)
                .withIntent(ProcessIntent.DELETE_COMPLETE)
                .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
                .exists())
        .describedAs("this partition reports drained once its last instance completes")
        .isTrue();
  }

  @Test
  public void shouldCarryDeleteHistoryIntentOnDrainReport() {
    // given - a draining definition whose deletion also requested its history to be deleted
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drain(metadata, true);

    // when - the last active instance completes
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then - the report carries the intent, since this partition removes the definition locally
    // right away and the deployment partition can no longer read it from state
    final var report =
        RecordingExporter.processRecords()
            .withRecordType(RecordType.COMMAND)
            .withIntent(ProcessIntent.DELETE_COMPLETE)
            .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .getFirst();
    assertThat(((ProcessRecord) report.getValue()).isDeleteHistory())
        .describedAs("the drain report carries the history deletion intent")
        .isTrue();
  }

  @Test
  public void shouldNotCarryDeleteHistoryIntentWhenNotRequested() {
    // given - a draining definition whose deletion did not request history deletion
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drain(metadata);

    // when - the last active instance completes
    engine.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    final var report =
        RecordingExporter.processRecords()
            .withRecordType(RecordType.COMMAND)
            .withIntent(ProcessIntent.DELETE_COMPLETE)
            .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .getFirst();
    assertThat(((ProcessRecord) report.getValue()).isDeleteHistory())
        .describedAs("the drain report does not request history deletion")
        .isFalse();
  }

  @Test
  public void shouldReportDrainedWhenLastDrainingInstanceTerminates() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deployWithJob(processId);
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    awaitJobCreated(processInstanceKey);
    drain(metadata);

    // when - the last active instance is terminated
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - the definition is removed locally and this partition reports drained
    assertDeletedLocally(metadata.getProcessDefinitionKey());
    assertThat(
            RecordingExporter.processRecords()
                .withRecordType(RecordType.COMMAND)
                .withIntent(ProcessIntent.DELETE_COMPLETE)
                .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
                .exists())
        .describedAs("this partition reports drained once its last instance terminates")
        .isTrue();
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
    drain(metadata);

    // when - only the first instance completes
    engine.job().ofInstance(firstInstanceKey).withType(JOB_TYPE).complete();

    // then - the definition is still draining, not yet reported drained (a new instance is still
    // rejected, which proves it has not been reported drained)
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);

    // when - the last instance completes
    engine.job().ofInstance(secondInstanceKey).withType(JOB_TYPE).complete();

    // then - this partition reports drained only after the last instance completes
    assertThat(
            RecordingExporter.processRecords()
                .withRecordType(RecordType.COMMAND)
                .withIntent(ProcessIntent.DELETE_COMPLETE)
                .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
                .exists())
        .describedAs("this partition reports drained only after the last instance completes")
        .isTrue();
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
    drain(child);

    // when - the child instance completes
    engine.job().ofInstance(childInstanceKey).withType(JOB_TYPE).complete();

    // then - the draining child definition is reported drained (finalize fires for child processes
    // too)
    assertThat(
            RecordingExporter.processRecords()
                .withRecordType(RecordType.COMMAND)
                .withIntent(ProcessIntent.DELETE_COMPLETE)
                .withProcessDefinitionKey(child.getProcessDefinitionKey())
                .exists())
        .describedAs(
            "draining child definition is reported drained when its call-activity instance"
                + " completes")
        .isTrue();
  }

  @Test
  public void shouldFullyDeleteWhenAllPartitionsReportDrained() {
    // given - a draining definition whose per-partition drain reports are outstanding for three
    // partitions
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();

    seedPendingDeletions(processDefinitionKey, 1, 2, 3);

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
                .withProcessDefinitionKey(processDefinitionKey)
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
  public void shouldDeleteHistoryWhenAllPartitionsReportDrained() {
    // given - a draining definition whose deletion also requested its history to be deleted
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata, true);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    seedPendingDeletions(processDefinitionKey, 1, 2, 3);

    // when - the last partition reports it has finished draining
    engine.writeRecords(
        drainReport(processDefinitionKey, metadata, 1, true),
        drainReport(processDefinitionKey, metadata, 2, true),
        drainReport(processDefinitionKey, metadata, 3, true));

    // then - the history of all its instances is deleted, followed by the definition itself. One
    // batch operation covers every partition; it distributes itself from here.
    final var batchOperation =
        RecordingExporter.batchOperationCreationRecords()
            .withIntent(BatchOperationIntent.CREATE)
            .getFirst()
            .getValue();
    assertThat(batchOperation.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);
    assertThat(batchOperation.getEntityFilter())
        .describedAs("the history of this definition's instances is deleted")
        .contains(
            "\"processDefinitionKeyOperations\":[{\"operator\":\"EQUALS\",\"values\":[%s]}]"
                .formatted(processDefinitionKey));
    assertThat(batchOperation.getFollowUpCommand())
        .isNotNull()
        .extracting(NestedRecordValue::getIntent, NestedRecordValue::getValueType)
        .containsExactly(HistoryDeletionIntent.DELETE, ValueType.HISTORY_DELETION);
    assertThat(batchOperation.getFollowUpCommand().getRecordValue())
        .describedAs("the definition itself is deleted from secondary storage afterwards")
        .asInstanceOf(InstanceOfAssertFactories.type(HistoryDeletionRecordValue.class))
        .extracting(
            HistoryDeletionRecordValue::getResourceKey, HistoryDeletionRecordValue::getResourceType)
        .containsExactly(processDefinitionKey, HistoryDeletionType.PROCESS_DEFINITION);
  }

  @Test
  public void shouldNotDeleteHistoryWhenNotRequested() {
    // given - a draining definition whose deletion did not request history deletion
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    seedPendingDeletions(processDefinitionKey, 1, 2, 3);

    // when - all partitions report they have finished draining
    engine.writeRecords(
        drainReport(processDefinitionKey, metadata, 1),
        drainReport(processDefinitionKey, metadata, 2),
        drainReport(processDefinitionKey, metadata, 3));

    // then - the definition is gone cluster-wide, but its history is kept. The window is bounded by
    // FULLY_DELETED (the terminal marker, emitted after any history deletion) so the absence check
    // does not block.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ProcessIntent.FULLY_DELETED)
                .batchOperationCreationRecords()
                .withIntent(BatchOperationIntent.CREATE)
                .exists())
        .describedAs("no history is deleted when the deletion did not ask for it")
        .isFalse();
  }

  @Test
  public void shouldDeleteHistoryOnlyOnceWhenDrainReportIsRedelivered() {
    // given - a draining definition that has already drained cluster-wide
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId);
    drain(metadata, true);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    seedPendingDeletions(processDefinitionKey, 1, 2);

    // when - the last partition's report is delivered twice
    engine.writeRecords(
        drainReport(processDefinitionKey, metadata, 1, true),
        drainReport(processDefinitionKey, metadata, 2, true),
        drainReport(processDefinitionKey, metadata, 2, true));

    // then - the redelivered report is rejected (proving the idempotency guard was exercised)
    RecordingExporter.processRecords()
        .onlyCommandRejections()
        .withIntent(ProcessIntent.DELETE_COMPLETE)
        .withProcessDefinitionKey(processDefinitionKey)
        .limit(1)
        .await();
    // and the history is deleted exactly once. The window is bounded by FULLY_DELETED (emitted once
    // on the report that cleared the last partition) so the count does not block.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ProcessIntent.FULLY_DELETED)
                .batchOperationCreationRecords()
                .withIntent(BatchOperationIntent.CREATE)
                .count())
        .describedAs("the history of the definition's instances is deleted exactly once")
        .isEqualTo(1);
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
                .setTenantId(metadata.getTenantId())
                .setDeleteHistory(deleteHistory));
  }

  private void seedPendingDeletions(final long processDefinitionKey, final int... partitionIds) {
    // seeding the aggregation set happens at delete time (#56978), so it is injected here directly
    // to drive the deployment-partition aggregation
    engine.pauseProcessing(Protocol.DEPLOYMENT_PARTITION);
    final var processState =
        ((MutableProcessingState) engine.getProcessingState()).getProcessState();
    for (final int partitionId : partitionIds) {
      processState.addPendingDeletion(processDefinitionKey, partitionId);
    }
    engine.resumeProcessing(Protocol.DEPLOYMENT_PARTITION);
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
        .get(0);
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
        .get(0);
  }

  /**
   * Puts the given process definition into the {@code DRAINING} state. Since no processor writes
   * the {@code DRAINING} event yet, the event is injected onto the log while the engine is stopped
   * so it is applied to state on the next start (replay), rather than being ignored during live
   * processing. TODO(#56978): drive draining via a real {@code RESOURCE_DELETION.DELETE} once that
   * change lands, and remove this injection helper.
   */
  private void drain(final ProcessMetadataValue metadata) {
    drain(metadata, false);
  }

  private void drain(final ProcessMetadataValue metadata, final boolean deleteHistory) {
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
                    .setDeleteHistory(deleteHistory)));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
