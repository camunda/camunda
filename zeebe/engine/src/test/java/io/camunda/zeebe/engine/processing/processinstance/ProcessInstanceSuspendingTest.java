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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
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
  void shouldCloseSubscriptionsWhileSuspending() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    final var correlationKey = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .receiveTask("receive")
                .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey =
        processInstanceClient
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    final var subscriptionClosing =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.DELETING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionClosing.getPosition())
        .isStrictlyBetween(suspending.getPosition(), suspended.getPosition());
  }

  @Test
  void shouldSuspendJobsWhileSuspending() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    engine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceClient = engine.createProcessInstanceClient();
    final long processInstanceKey = processInstanceClient.ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when
    final var suspended = processInstanceClient.withInstanceKey(processInstanceKey).suspend();

    // then
    final var suspending =
        RecordingExporter.processInstanceRecords(SUSPENDING)
            .withRecordKey(processInstanceKey)
            .getFirst();
    final var jobSuspended =
        RecordingExporter.jobRecords(JobIntent.SUSPENDED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobSuspended.getPosition())
        .isStrictlyBetween(suspending.getPosition(), suspended.getPosition());
  }
}
