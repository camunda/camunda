/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.SUSPENDING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.perf.TestEngine;
import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceSuspendingTest {

  private TestContext context;
  private TestEngine engine;

  @BeforeEach
  void setUp() throws IOException {
    context = TestEngine.createTestContext();
    engine = TestEngine.createSinglePartitionEngine(context);
  }

  @AfterEach
  void tearDown() {
    context.close();
    RecordingExporter.reset();
  }

  @Test
  void shouldCloseSubscriptionsOfRootAndNestedElements() {
    // given - a subscription on the process instance itself (event subprocess) and subscriptions
    // below the root, which the walk only reaches by descending
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    final var correlationKey = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .eventSubProcess(
                    "event-sub",
                    sub ->
                        sub.startEvent()
                            .interrupting(false)
                            .message(
                                m ->
                                    m.name(messageName + "-start")
                                        .zeebeCorrelationKeyExpression("key"))
                            .endEvent())
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .receiveTask(
                    "receive",
                    task ->
                        task.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                            .multiInstance()
                            .parallel()
                            .zeebeInputCollectionExpression("[1, 2, 3]"))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey =
        processInstanceClient
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .create();
    final var createdSubscriptionKeys =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(4)
            .map(Record::getKey)
            .toList();
    final var messageSideSubscriptionKeys =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(4)
            .map(Record::getKey)
            .toList();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(closingSubscriptionKeysBetween(suspending, suspended))
        .containsExactlyInAnyOrderElementsOf(createdSubscriptionKeys);
    // the close must name the message-side subscription, so it cannot delete a newer one
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETE)
                .withProcessInstanceKey(processInstanceKey)
                .limit(4))
        .extracting(r -> r.getValue().getSubscriptionKey())
        .containsExactlyInAnyOrderElementsOf(messageSideSubscriptionKeys);
  }

  @Test
  void shouldCloseEverySubscriptionOfEventBasedGateway() {
    // given - the gateway keeps one subscription per message branch on its own element instance
    final var processId = Strings.newRandomValidBpmnId();
    final var messageA = Strings.newRandomValidBpmnId();
    final var messageB = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .eventBasedGateway("gateway")
                .intermediateCatchEvent(
                    "a", e -> e.message(m -> m.name(messageA).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "b", e -> e.message(m -> m.name(messageB).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey =
        processInstanceClient
            .ofBpmnProcessId(processId)
            .withVariable("key", Strings.newRandomValidBpmnId())
            .create();
    final var createdSubscriptionKeys =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(Record::getKey)
            .toList();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(closingSubscriptionKeysBetween(suspending, suspended))
        .containsExactlyInAnyOrderElementsOf(createdSubscriptionKeys);
  }

  @Test
  void shouldSuspendJobsNestedInSubprocessAndMultiInstance() {
    // given - jobs below the root, which the walk only reaches by descending
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .serviceTask(
                    "task",
                    task ->
                        task.zeebeJobType(jobType)
                            .multiInstance()
                            .parallel()
                            .zeebeInputCollectionExpression("[1, 2, 3]"))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey = processInstanceClient.ofBpmnProcessId(processId).create();
    final var createdJobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(suspendedJobKeysBetween(suspending, suspended))
        .containsExactlyInAnyOrderElementsOf(createdJobKeys);
  }

  @Test
  void shouldNotSuspendAnythingInCalledProcessInstance() {
    // given - the called instance is not a child in the element instance tree, so the walk does
    // not reach its job, message subscription or user task
    final var processId = Strings.newRandomValidBpmnId();
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task", task -> task.zeebeJobType(jobType))
                .endEvent()
                .moveToLastGateway()
                .receiveTask("receive")
                .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .moveToLastGateway()
                .userTask("user-task", task -> task.zeebeUserTask())
                .endEvent()
                .done())
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .callActivity("call", call -> call.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey =
        processInstanceClient
            .ofBpmnProcessId(processId)
            .withVariable("key", Strings.newRandomValidBpmnId())
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .await();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED).withElementId("user-task").await();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(suspendedJobKeysBetween(suspending, suspended)).isEmpty();
    assertThat(closingSubscriptionKeysBetween(suspending, suspended)).isEmpty();
    assertThat(suspendedUserTaskKeysBetween(suspending, suspended)).isEmpty();
  }

  private static List<Long> suspendedJobKeysBetween(
      final Record<?> suspending, final Record<?> suspended) {
    return RecordingExporter.records()
        .between(suspending, suspended)
        .jobRecords()
        .withIntent(JobIntent.SUSPENDED)
        .map(Record::getKey)
        .toList();
  }

  private static List<Long> closingSubscriptionKeysBetween(
      final Record<?> suspending, final Record<?> suspended) {
    return RecordingExporter.records()
        .between(suspending, suspended)
        .processMessageSubscriptionRecords()
        .withIntent(ProcessMessageSubscriptionIntent.DELETING)
        .map(Record::getKey)
        .toList();
  }

  private static List<Long> suspendedUserTaskKeysBetween(
      final Record<?> suspending, final Record<?> suspended) {
    return RecordingExporter.records()
        .between(suspending, suspended)
        .userTaskRecords()
        .withIntent(UserTaskIntent.SUSPENDED)
        .map(Record::getKey)
        .toList();
  }
}
