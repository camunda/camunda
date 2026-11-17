/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;

/**
 * Reproduces gaps in the draining deletion of process definitions. Each test asserts the expected
 * behavior, so it fails as long as the gap exists. See also {@link
 * DrainingProcessDefinitionRaceTest}.
 */
public class DrainingProcessDefinitionGapsTest {

  private static final String JOB_TYPE = "task";

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  // Finding 1: the start event hand-over only runs when the deleted version is the latest
  // version, and a DRAINING version still counts as latest. A version that took over the start
  // events from a draining latest therefore keeps them when it is deleted.

  @Test
  public void shouldRemoveTimerStartEventWhenDeletingVersionThatTookItOver() {
    // given - three versions with a timer start event; v3 holds the timer
    final var processId = helper.getBpmnProcessId();
    final var timerStart = (Consumer<StartEventBuilder>) start -> start.timerWithCycle("R/PT1H");
    deployWithStartEvent(processId, "task-v1", timerStart);
    final long v2Key =
        deployWithStartEvent(processId, "task-v2", timerStart).getProcessDefinitionKey();
    final long v3Key =
        deployWithStartEvent(processId, "task-v3", timerStart).getProcessDefinitionKey();

    // and - v3 is draining, so its timer is handed over to v2
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).withVersion(3).create());
    drainViaDeletion(v3Key);

    // and - v2 has no instances, so it is deleted right away
    deleteAndAwaitDeleted(v2Key);

    // then - v2 got a timer twice (its own deployment and the hand-over from v3), and both are
    // removed: the first by the deployment of v3, the second when v2 is deleted
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(v2Key)
                .limit(2)
                .count())
        .describedAs("the timer start event v2 took over must be removed when v2 is deleted")
        .isEqualTo(2);

    // and - a later deployment of any other process is not rejected because of a timer whose
    // process definition no longer exists
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId + "-other").startEvent().endEvent().done())
        .deploy();
  }

  @Test
  public void shouldHandOverMessageStartEventWhenDeletingVersionThatTookItOver() {
    // given - three versions with a message start event; v3 holds the subscription
    final var processId = helper.getBpmnProcessId();
    final var messageName = processId + "-message";
    final var messageStart = (Consumer<StartEventBuilder>) start -> start.message(messageName);
    final long v1Key =
        deployWithStartEvent(processId, "task-v1", messageStart).getProcessDefinitionKey();
    final long v2Key =
        deployWithStartEvent(processId, "task-v2", messageStart).getProcessDefinitionKey();
    final long v3Key =
        deployWithStartEvent(processId, "task-v3", messageStart).getProcessDefinitionKey();

    // and - v3 is draining, so its subscription is handed over to v2
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).withVersion(3).create());
    drainViaDeletion(v3Key);

    // and - v2 has no instances, so it is deleted right away
    deleteAndAwaitDeleted(v2Key);

    // when
    engine.message().withName(messageName).withCorrelationKey("key").publish();

    // then - v1, the only active version, is started
    assertThat(processInstanceActivated(v1Key))
        .describedAs("the message start event must be handed over to v1 when v2 is deleted")
        .isTrue();
  }

  // Finding 2: a deployment only closes the start event subscriptions of the version directly
  // below the new one. While the latest is draining, that is the draining version, which holds
  // nothing; the version that took over the start events stays subscribed.

  @Test
  public void shouldOnlyStartNewVersionBySignalWhenDeployingWhileLatestDrains() {
    // given - two versions with a signal start event; v2 holds the subscription
    final var processId = helper.getBpmnProcessId();
    final var signalName = processId + "-signal";
    final var signalStart = (Consumer<StartEventBuilder>) start -> start.signal(signalName);
    final long v1Key =
        deployWithStartEvent(processId, "task-v1", signalStart).getProcessDefinitionKey();
    final long v2Key =
        deployWithStartEvent(processId, "task-v2", signalStart).getProcessDefinitionKey();

    // and - v2 is draining, so its subscription is handed over to v1
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(processId).withVersion(2).create());
    drainViaDeletion(v2Key);

    // and - a new version v3 is deployed
    final long v3Key =
        deployWithStartEvent(processId, "task-v3", signalStart).getProcessDefinitionKey();

    // when
    engine.signal().withSignalName(signalName).broadcast();

    // then - only the new latest version is started
    assertThat(processInstanceActivated(v3Key)).describedAs("v3 is started").isTrue();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_ACTIVATING)
                        .withProcessDefinitionKey(v1Key)
                        .withElementType(BpmnElementType.PROCESS)
                        .exists()))
        .describedAs("v1 must no longer be subscribed once v3 is deployed")
        .isFalse();
  }

  // Finding 4: "latest" resolution that does not skip a draining latest version.

  @Test
  public void shouldCallLatestActiveVersionWhenLatestCalledVersionIsDraining() {
    // given - child v1 is active and child v2 is draining
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final long childV1Key = deployWithJob(childId, "task-v1").getProcessDefinitionKey();
    final long childV2Key = deployWithJob(childId, "task-v2").getProcessDefinitionKey();
    awaitJobCreated(engine.processInstance().ofBpmnProcessId(childId).withVersion(2).create());
    drainViaDeletion(childV2Key);

    // and - a parent calling the latest child version
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

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then - the active child v1 is called, like create-by-id would do
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withParentProcessInstanceKey(parentInstanceKey)
                .withProcessDefinitionKey(childV1Key)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("the call activity must call the latest active child version v1")
        .isTrue();
  }

  @Test
  public void shouldCorrelateBufferedMessageToLatestActiveVersionWhenLatestIsDraining() {
    // given - two versions with a message start event; v2 holds the subscription
    final var processId = helper.getBpmnProcessId();
    final var messageName = processId + "-message";
    final var messageStart = (Consumer<StartEventBuilder>) start -> start.message(messageName);
    final long v1Key =
        deployWithStartEvent(processId, "task-v1", messageStart).getProcessDefinitionKey();
    final long v2Key =
        deployWithStartEvent(processId, "task-v2", messageStart).getProcessDefinitionKey();

    // and - a message starts a v2 instance with correlation key "key"
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey("key")
        .withTimeToLive(Duration.ofHours(1))
        .publish();
    final long v2InstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessDefinitionKey(v2Key)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    awaitJobCreated(v2InstanceKey);

    // and - a second message with the same correlation key is buffered, because the v2 instance
    // still holds the correlation key
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey("key")
        .withTimeToLive(Duration.ofHours(1))
        .publish();

    // and - v2 is draining, so its subscription is handed over to v1
    drainViaDeletion(v2Key);

    // when - the v2 instance completes and releases the correlation key
    engine.job().ofInstance(v2InstanceKey).withType(JOB_TYPE).complete();

    // then - the buffered message starts v1, which now holds the subscription
    assertThat(processInstanceActivated(v1Key))
        .describedAs("the buffered message must be correlated to the active v1")
        .isTrue();
  }

  // Finding 5: the guard rejects every ACTIVATE_ELEMENT of a PROCESS element of a draining
  // definition, including for instances that already exist.

  @Test
  public void shouldContinueExistingInstanceAfterIncidentResolutionWhenDefinitionIsDraining() {
    // given - a process start execution listener whose job type cannot be evaluated, so the
    // process element gets an incident while it is activating
    final var processId = helper.getBpmnProcessId();
    final long processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .zeebeExecutionListener(el -> el.start().typeExpression("listenerType"))
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // and - the definition is deleted while the instance is stuck, so it is draining
    drainViaDeletion(processDefinitionKey);

    // when - the cause is fixed and the incident is resolved
    engine
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("listenerType", "listener"))
        .update();
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then - the existing instance continues and creates the listener job
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType("listener")
                .exists())
        .describedAs("the existing instance must continue after the incident is resolved")
        .isTrue();
  }

  private ProcessMetadataValue deployWithJob(final String processId, final String taskId) {
    return deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
  }

  // A none start event to create instances that keep a version draining, plus the given start
  // event. Each version gets a distinct task id so every deployment creates a new version.
  private ProcessMetadataValue deployWithStartEvent(
      final String processId,
      final String taskId,
      final Consumer<StartEventBuilder> startEventConfigurer) {
    final var builder =
        Bpmn.createExecutableProcess(processId)
            .startEvent("none-start")
            .serviceTask(taskId, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .moveToProcess(processId)
            .startEvent("other-start");
    startEventConfigurer.accept(builder);
    return deploy(builder.serviceTask(taskId + "-other", t -> t.zeebeJobType(JOB_TYPE)).done());
  }

  private ProcessMetadataValue deploy(final BpmnModelInstance model) {
    return engine
        .deployment()
        .withXmlResource(model)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private void awaitJobCreated(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();
  }

  private void drainViaDeletion(final long processDefinitionKey) {
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();
  }

  private void deleteAndAwaitDeleted(final long processDefinitionKey) {
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();
  }

  private boolean processInstanceActivated(final long processDefinitionKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .exists();
  }
}
