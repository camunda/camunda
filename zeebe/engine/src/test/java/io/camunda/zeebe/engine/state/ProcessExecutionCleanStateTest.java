/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessExecutionCleanStateTest {

  private static final String PROCESS_ID = "process";

  private static final List<ZbColumnFamilies> IGNORE_NON_EMPTY_COLUMNS =
      List.of(
          ZbColumnFamilies.DEFAULT,
          ZbColumnFamilies.KEY,
          ZbColumnFamilies.PROCESS_VERSION,
          ZbColumnFamilies.PROCESS_CACHE,
          ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
          ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID,
          ZbColumnFamilies.PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY,
          ZbColumnFamilies.MESSAGE_STATS,
          ZbColumnFamilies.MIGRATIONS_STATE,
          ZbColumnFamilies.DEPLOYMENT_RAW,
          ZbColumnFamilies.USERS,
          ZbColumnFamilies.USERNAME_BY_USER_KEY,
          ZbColumnFamilies.PERMISSIONS,
          ZbColumnFamilies.OWNER_TYPE_BY_OWNER_KEY,
          ZbColumnFamilies.ROLES,
          ZbColumnFamilies.ENTITY_BY_ROLE,
          ZbColumnFamilies.TENANTS,
          ZbColumnFamilies.AUTHORIZATIONS,
          ZbColumnFamilies.AUTHORIZATION_KEYS_BY_OWNER);

  @Rule public EngineRule engineRule = EngineRule.singlePartition();

  private ProcessingState processingState;

  @Before
  public void init() {
    processingState = engineRule.getProcessingState();

    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithServiceTask() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    engineRule
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariable("y", 2)
        .complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithSubprocess() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    subProcess ->
                        subProcess
                            .zeebeInputExpression("x", "y")
                            .zeebeOutputExpression("y", "z")
                            .embeddedSubProcess()
                            .startEvent()
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithMultiInstance() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("test")
                            .multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")
                                        .zeebeOutputCollection("results")
                                        .zeebeOutputElementExpression("result")))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", List.of(1))
            .create();

    engineRule
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariable("result", 2)
        .complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithTimerEvent() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT0S"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithMessageEvent() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent(
                    "message",
                    e ->
                        e.message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                            .zeebeOutputExpression("x", "y"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "key-1")
            .create();

    // when
    final var timeToLive = Duration.ofSeconds(10);
    engineRule
        .message()
        .withName("message")
        .withCorrelationKey("key-1")
        .withTimeToLive(timeToLive)
        .withVariables(Map.of("x", 1))
        .publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    engineRule.increaseTime(
        timeToLive.plus(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL));

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithMessageStartEvent() {
    // given
    final var deployment =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                    .zeebeOutputExpression("x", "y")
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    final var timeToLive = Duration.ofSeconds(10);
    final var messagePublished =
        engineRule
            .message()
            .withName("message")
            .withCorrelationKey("key-1")
            .withTimeToLive(timeToLive)
            .withVariables(Map.of("x", 1))
            .publish();

    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessDefinitionKey(processDefinitionKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    engineRule.increaseTime(
        timeToLive.plus(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL));

    // deploy new process without message start event to close the open subscription
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.DELETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithErrorEvent() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .boundaryEvent("error", b -> b.error("ERROR"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    // when
    engineRule
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withErrorCode("ERROR")
        .throwError();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithIncident() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    // when
    engineRule.job().ofInstance(processInstanceKey).withType("test").withRetries(0).fail();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    engineRule.job().withKey(incidentCreated.getValue().getJobKey()).withRetries(1).updateRetries();

    engineRule
        .incident()
        .ofInstance(processInstanceKey)
        .withKey(incidentCreated.getKey())
        .resolve();

    engineRule
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariable("y", 2)
        .complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithExclusiveGateway() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .exclusiveGateway()
                .sequenceFlowId("s1")
                .conditionExpression("x > 10")
                .endEvent()
                .moveToLastGateway()
                .sequenceFlowId("s2")
                .conditionExpression("x <= 10")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithParallelGateway() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .endEvent()
                .moveToNode("fork")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithEventBasedGateway() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .eventBasedGateway()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT0S"))
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "message",
                    e -> e.message(m -> m.name("message").zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "key-1")
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithEventSubprocess() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess",
                    subprocess ->
                        subprocess
                            .startEvent()
                            .interrupting(true)
                            .timerWithDuration("PT0.1S")
                            .endEvent())
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithCallActivity() {
    // given
    final var childProcess = Bpmn.createExecutableProcess("child").startEvent().endEvent().done();
    final var parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("child"))
            .endEvent()
            .done();

    engineRule
        .deployment()
        .withXmlResource("child.bpmn", childProcess)
        .withXmlResource("parent.bpmn", parentProcess)
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessCreatedWithResult() {
    // given
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("x", 1)
            .withResult()
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessCanceled() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engineRule.processInstance().withInstanceKey(processInstanceKey).cancel();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithTimerStartEvent() {
    // given
    final var deployment =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .timerWithCycle("R/PT10S")
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    // deploy new process without timer start event to delete the timer
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    RecordingExporter.timerRecords(TimerIntent.CANCELED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithMessageStartEventAndRedeployWithout() {
    // given
    final var deployment =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .message(m -> m.name("msg").zeebeCorrelationKey("=123"))
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    // deploy new process without msg start event to delete the subscription and event scope
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.DELETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithTriggerTimerStartEvent() {
    // given
    final var deployment =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .timerWithDate("=now() + duration(\"PT15S\")")
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    engineRule.awaitProcessingOf(
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst());

    engineRule.increaseTime(Duration.ofSeconds(15));

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithTimerStartEventRedeployment() {
    // given
    final var deployment =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .timerWithCycle("R/PT10S")
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    final var deploy2 =
        engineRule
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .timerWithCycle("R/PT5S")
                    .endEvent()
                    .done())
            .deploy();

    final var processDefinitionKey2 =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    // deploy new process without timer start event to delete the timer
    engineRule
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    RecordingExporter.timerRecords(TimerIntent.CANCELED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    RecordingExporter.timerRecords(TimerIntent.CANCELED)
        .withProcessDefinitionKey(processDefinitionKey2)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testTerminatingProcessWithServiceTask() {
    // given
    engineRule
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engineRule.job().ofInstance(processInstanceKey).withType("test").withRetries(0).fail();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    engineRule.processInstance().withInstanceKey(processInstanceKey).cancel();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithCompensationEventTriggered() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .endEvent()
            .compensateEventDefinition()
            .done();

    engineRule.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engineRule.job().ofInstance(processInstanceKey).withType("A").complete();
    engineRule.job().ofInstance(processInstanceKey).withType("Undo-A").complete();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertThatStateIsEmpty();
  }

  @Test
  public void testProcessWithCompensationEventNotTriggered() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "A",
                task ->
                    task.zeebeJobType("A")
                        .boundaryEvent()
                        .compensation(
                            compensation ->
                                compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
            .endEvent()
            .done();

    engineRule.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        engineRule.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    engineRule.job().ofInstance(processInstanceKey).withType("A").complete();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertThatStateIsEmpty();
  }

  private void assertThatStateIsEmpty() {
    // sometimes the state takes few moments until is empty
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var nonEmptyColumns =
                  Arrays.stream(ZbColumnFamilies.values())
                      .filter(not(IGNORE_NON_EMPTY_COLUMNS::contains))
                      .filter(not(processingState::isEmpty))
                      .collect(Collectors.toList());

              assertThat(nonEmptyColumns).describedAs("Expected all columns to be empty").isEmpty();
            });
  }
}
