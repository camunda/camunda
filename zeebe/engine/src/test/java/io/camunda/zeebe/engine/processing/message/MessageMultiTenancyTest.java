/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageMultiTenancyTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();
  private final MessageSender messageSender;

  public MessageMultiTenancyTest(final MessageSender messageSender) {
    this.messageSender = messageSender;
  }

  @Parameters(name = "{0}")
  public static List<MessageSender> data() {
    return Arrays.asList(new MessagePublishSender(), new MessageCorrelateSender());
  }

  @Test
  public void shouldStartAndCompleteProcessWithMessageStartEvent() {
    // given
    final var tenantId = "tenant" + UUID.randomUUID();
    final var processId = Strings.newRandomValidBpmnId();
    final String messageName = "msg" + UUID.randomUUID();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message(messageName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    messageSender.sendExpectCorrelation(messageName, "", tenantId);

    // then
    assertMessagePublishedForTenantId(messageName, tenantId);
    assertMessageStartEventSubscriptionCreatedForTenant(processId, messageName, tenantId);
    assertMessageStartEventSubscriptionCorrelatedForTenant(processId, messageName, tenantId);
    assertProcessInstanceCompleted(processId, tenantId);
  }

  @Test
  public void shouldStartAndCompleteProcessWithIntermediateCatchEvent() {
    // given
    final var tenantId = "tenant" + UUID.randomUUID();
    final var processId = Strings.newRandomValidBpmnId();
    final String messageName = "msg" + UUID.randomUUID();
    final String correlationKey = "corr" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .withTenantId(tenantId)
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(messageName)
        .withCorrelationKey(correlationKey)
        .limit(1)
        .await();

    // when
    messageSender.sendExpectCorrelation(messageName, correlationKey, tenantId);

    // then
    assertMessagePublishedForTenantId(messageName, tenantId);
    assertProcessMessageSubscriptionCreatedForTenantId(tenantId, messageName, processInstanceKey);
    assertProcessMessageSubscriptionCorrelatedForTenantId(
        tenantId, messageName, processInstanceKey);
    assertMessageSubscriptionCreatedForTenantId(tenantId, messageName, processInstanceKey);
    assertMessageSubscriptionCorrelatedForTenantId(tenantId, messageName, processInstanceKey);
    assertProcessInstanceCompleted(processId, tenantId);
  }

  @Test
  public void shouldNotCorrelateToMessageStartOfDifferentTenant() {
    // given
    final var tenantId = "tenant" + UUID.randomUUID();
    final var otherTenant = "tenant" + UUID.randomUUID();
    final var processId = Strings.newRandomValidBpmnId();
    final String messageName = "msg" + UUID.randomUUID();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .message(messageName)
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    // when
    messageSender.sendExpectNoCorrelation(messageName, "", otherTenant);

    // then
    assertMessageStartEventSubscriptionCreatedForTenant(processId, messageName, tenantId);
    assertMessagePublishedForTenantId(messageName, otherTenant);
    assertMessageStartEventSubscriptionNotCorrelatedForTenant(processId, messageName, tenantId);
    assertMessageStartEventSubscriptionNotCorrelatedForTenant(processId, messageName, otherTenant);
    assertProcessInstanceNotCompleted(processId, tenantId);
  }

  @Test
  public void shouldNotCorrelateToIntermediateCatchEventOfDifferentTenant() {
    // given
    final var tenantId = "tenant" + UUID.randomUUID();
    final var otherTenant = "tenant" + UUID.randomUUID();
    final var processId = Strings.newRandomValidBpmnId();
    final String messageName = "msg" + UUID.randomUUID();
    final String correlationKey = "corr" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .withTenantId(tenantId)
            .create();

    // when
    messageSender.sendExpectNoCorrelation(messageName, correlationKey, otherTenant);

    // then
    assertProcessMessageSubscriptionCreatedForTenantId(tenantId, messageName, processInstanceKey);
    assertMessageSubscriptionCreatedForTenantId(tenantId, messageName, processInstanceKey);
    assertMessagePublishedForTenantId(messageName, otherTenant);
    assertProcessMessageSubscriptionNotCorrelatedForTenantId(
        tenantId, messageName, processInstanceKey);
    assertProcessMessageSubscriptionNotCorrelatedForTenantId(
        otherTenant, messageName, processInstanceKey);
    assertMessageSubscriptionNotCorrelatedForTenantId(tenantId, messageName, processInstanceKey);
    assertMessageSubscriptionNotCorrelatedForTenantId(otherTenant, messageName, processInstanceKey);
    assertProcessInstanceNotCompleted(processId, tenantId);
  }

  @Test
  public void shouldCorrelateBufferedMessageToCorrectTenant() {
    if (messageSender instanceof MessageCorrelateSender) {
      // The message correlate command does not support message buffering.
      return;
    }

    // given a buffered message
    final var tenantId = "tenant" + UUID.randomUUID();
    final var otherTenant = "otherTenant" + UUID.randomUUID();
    final var processId = Strings.newRandomValidBpmnId();
    final String messageName = "msg" + UUID.randomUUID();
    final String correlationKey = "corr" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    c -> c.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .withTenantId(otherTenant)
        .deploy();
    messageSender.sendExpectCorrelation(messageName, correlationKey, tenantId);

    // when creating a process instance for each tenant
    final long processInstanceKeyOtherTenant =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .withTenantId(otherTenant)
            .create();
    final long processInstanceKeyTenant =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("key", correlationKey)
            .withTenantId(tenantId)
            .create();

    // then only the process matching the message tenant is correlated
    assertMessagePublishedForTenantId(messageName, tenantId);
    assertProcessMessageSubscriptionCreatedForTenantId(
        otherTenant, messageName, processInstanceKeyOtherTenant);
    assertMessageSubscriptionCreatedForTenantId(tenantId, messageName, processInstanceKeyTenant);
    assertMessageSubscriptionCreatedForTenantId(
        otherTenant, messageName, processInstanceKeyOtherTenant);
    assertProcessMessageSubscriptionCorrelatedForTenantId(
        tenantId, messageName, processInstanceKeyTenant);
    assertProcessMessageSubscriptionNotCorrelatedForTenantId(
        otherTenant, messageName, processInstanceKeyOtherTenant);
    assertMessageSubscriptionCorrelatedForTenantId(tenantId, messageName, processInstanceKeyTenant);
    assertMessageSubscriptionNotCorrelatedForTenantId(
        otherTenant, messageName, processInstanceKeyOtherTenant);
    assertProcessInstanceCompleted(processId, tenantId);
    assertProcessInstanceNotCompleted(processId, otherTenant);
  }

  private static void assertMessageSubscriptionCreatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .limit(1))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(tuple(MessageSubscriptionIntent.CREATED, tenantId));
  }

  private static void assertMessageSubscriptionCorrelatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withIntents(
                    MessageSubscriptionIntent.CORRELATING, MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .limit(2))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(
            tuple(MessageSubscriptionIntent.CORRELATING, tenantId),
            tuple(MessageSubscriptionIntent.CORRELATED, tenantId));
  }

  private static void assertMessageSubscriptionNotCorrelatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    final var finalPosition = getFinalPosition();

    assertThat(
            RecordingExporter.records()
                .between(0, finalPosition)
                .messageSubscriptionRecords()
                .withIntents(
                    MessageSubscriptionIntent.CORRELATING, MessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withTenantId(tenantId))
        .isEmpty();
  }

  private static void assertProcessMessageSubscriptionCreatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withIntents(
                    ProcessMessageSubscriptionIntent.CREATING,
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withTenantId(tenantId)
                .limit(2))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(
            tuple(ProcessMessageSubscriptionIntent.CREATING, tenantId),
            tuple(ProcessMessageSubscriptionIntent.CREATED, tenantId));
  }

  private static void assertProcessMessageSubscriptionCorrelatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withTenantId(tenantId)
                .limit(1))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(tuple(ProcessMessageSubscriptionIntent.CORRELATED, tenantId));
  }

  private static void assertProcessMessageSubscriptionNotCorrelatedForTenantId(
      final String tenantId, final String messageName, final long processInstanceKey) {
    final long finalPosition = getFinalPosition();

    assertThat(
            RecordingExporter.records()
                .between(0, finalPosition)
                .processMessageSubscriptionRecords()
                .withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(messageName)
                .withTenantId(tenantId))
        .isEmpty();
  }

  private static void assertMessagePublishedForTenantId(
      final String messageName, final String tenantId) {
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                .withName(messageName)
                .findFirst())
        .isPresent()
        .get()
        .extracting(r -> r.getValue().getTenantId())
        .isEqualTo(tenantId);
  }

  private static void assertMessageStartEventSubscriptionCreatedForTenant(
      final String processId, final String messageName, final String tenantId) {
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CREATED)
                .withBpmnProcessId(processId)
                .withMessageName(messageName)
                .withTenantId(tenantId)
                .limit(1))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(tuple(MessageStartEventSubscriptionIntent.CREATED, tenantId));
  }

  private static void assertMessageStartEventSubscriptionCorrelatedForTenant(
      final String processId, final String messageName, final String tenantId) {
    assertThat(
            RecordingExporter.messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                .withBpmnProcessId(processId)
                .withMessageName(messageName)
                .withTenantId(tenantId)
                .limit(1))
        .extracting(Record::getIntent, r -> r.getValue().getTenantId())
        .containsExactly(tuple(MessageStartEventSubscriptionIntent.CORRELATED, tenantId));
  }

  private static void assertMessageStartEventSubscriptionNotCorrelatedForTenant(
      final String processId, final String messageName, final String tenantId) {
    final long finalPosition = getFinalPosition();

    assertThat(
            RecordingExporter.records()
                .between(0, finalPosition)
                .messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                .withBpmnProcessId(processId)
                .withMessageName(messageName)
                .withTenantId(tenantId))
        .isEmpty();
  }

  private static void assertProcessInstanceCompleted(
      final String processId, final String tenantId) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withBpmnProcessId(processId)
                .withTenantId(tenantId)
                .limitToProcessInstanceCompleted())
        .isNotEmpty();
  }

  private static void assertProcessInstanceNotCompleted(
      final String processId, final String tenantId) {
    final long finalPosition = getFinalPosition();

    assertThat(
            RecordingExporter.records()
                .between(0, finalPosition)
                .processInstanceRecords()
                .withBpmnProcessId(processId)
                .withTenantId(tenantId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS))
        .isEmpty();
  }

  private static long getFinalPosition() {
    return ENGINE
        .decision()
        .ofDecisionId(UUID.randomUUID().toString())
        .expectRejection()
        .evaluate()
        .getPosition();
  }

  static final class MessagePublishSender extends MessageSender {
    @Override
    public void sendExpectCorrelation(
        final String messageName, final String correlationKey, final String tenantId) {
      ENGINE
          .message()
          .withTenantId(tenantId)
          .withName(messageName)
          .withCorrelationKey(correlationKey)
          .publish();
    }

    @Override
    void sendExpectNoCorrelation(
        final String messageName, final String correlationKey, final String tenantId) {
      // Send the exact same command as for a correlation expectation. The test message client
      // considers it a success once the message is published. This happens regardless of
      // correlation.
      sendExpectCorrelation(messageName, correlationKey, tenantId);
    }
  }

  static final class MessageCorrelateSender extends MessageSender {
    @Override
    public void sendExpectCorrelation(
        final String messageName, final String correlationKey, final String tenantId) {
      ENGINE
          .messageCorrelation()
          .withTenantId(tenantId)
          .withName(messageName)
          .withCorrelationKey(correlationKey)
          .correlate();
    }

    @Override
    void sendExpectNoCorrelation(
        final String messageName, final String correlationKey, final String tenantId) {
      ENGINE
          .messageCorrelation()
          .withTenantId(tenantId)
          .withName(messageName)
          .withCorrelationKey(correlationKey)
          .expectNotCorrelated()
          .correlate();
    }
  }

  abstract static class MessageSender {
    abstract void sendExpectCorrelation(
        String messageName, final String correlationKey, String tenantId);

    abstract void sendExpectNoCorrelation(
        String messageName, final String correlationKey, String tenantId);

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }
}
