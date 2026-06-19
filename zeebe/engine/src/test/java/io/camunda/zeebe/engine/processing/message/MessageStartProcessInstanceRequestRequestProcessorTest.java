/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the {@code P_B}-side processor of the cross-partition message-start handshake in
 * isolation: a directly-written {@link MessageStartProcessInstanceRequestIntent#REQUEST} command is
 * consumed and translated into one of the three reply commands. The routing flip that drives this
 * handshake from a published message lands in a later commit; until then, this is the only
 * end-to-end exercise of the processor.
 *
 * <p>Tests run on a single-partition engine and encode the source partition in {@code messageKey}
 * as partition {@code 1}, so the reply command is written locally and observable via {@link
 * RecordingExporter}. No reply-side processor is registered yet (lands in a later commit), so the
 * reply command stays a recorded command — exactly what we want to assert on here.
 */
public final class MessageStartProcessInstanceRequestRequestProcessorTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String START_EVENT_ID = "start";
  private static final String CORRELATION_KEY = "ck";
  private static final String BUSINESS_ID = "biz-1";
  private static final long SOURCE_MESSAGE_KEY = Protocol.encodePartitionId(1, 42);
  private static final long SUBSCRIPTION_PLACEHOLDER_KEY = 9_999L;

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true))
          .withInitialClusterVersionAtMax();

  @Test
  public void shouldStartProcessInstanceAndReplyStartedWhenSubscriptionExistsAndBusinessIdIsFree() {
    // given a deployed process with a message-start event
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();

    // when a REQUEST is delivered to this partition (acting as P_B)
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey)));

    // then the request is acknowledged with REQUESTED, a new PI is activated, and a START reply
    // command carrying the new PI key is dispatched back to P_K
    final var jobCreated = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();
    final long newPiKey = jobCreated.getValue().getProcessInstanceKey();
    assertThat(newPiKey).isPositive();

    final var requested =
        RecordingExporter.messageStartProcessInstanceRequestRecords(
                MessageStartProcessInstanceRequestIntent.REQUESTED)
            .getFirst();
    assertThat(requested.getRecordType()).isEqualTo(RecordType.EVENT);
    assertOriginalRequestPreserved(requested.getValue(), BUSINESS_ID, subscriptionKey);

    final var startReply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.START);
    assertOriginalRequestPreserved(startReply, BUSINESS_ID, subscriptionKey);
    assertThat(startReply.getProcessInstanceKey())
        .as("START reply carries the new PI key for P_K to apply")
        .isEqualTo(newPiKey);

    assertNoOtherReply(MessageStartProcessInstanceRequestIntent.START);
  }

  @Test
  public void shouldReplyUniquenessRejectedWhenBusinessIdIsAlreadyHeldByActivePI() {
    // given an active PI already holds the businessId on this partition
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when a REQUEST arrives for the same businessId
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey)));

    // then it is rejected for uniqueness and no second PI is created
    final var reply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS);
    assertOriginalRequestPreserved(reply, BUSINESS_ID, subscriptionKey);
    assertThat(reply.getProcessInstanceKey())
        .as("rejection replies do not carry a PI key")
        .isEqualTo(-1L);

    // bound the PI-creation scan on the rejection reply as a deterministic terminal so the
    // assertion does not have to wait for a second creation that should never happen
    final long startedPis =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent()
                            == MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(startedPis).isEqualTo(1L);
    assertNoOtherReply(MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS);
  }

  @Test
  public void shouldTreatBannedHolderAsAvailableAndStartFreshPI() {
    // given an active PI holds the businessId, then is banned (e.g. via incident handling)
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    final long bannedPiKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    engine.banInstanceInNewTransaction(1, bannedPiKey);

    // when a REQUEST arrives for the same businessId
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey)));

    // then the banned PI is excluded from the uniqueness check and a fresh PI starts
    final var startReply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.START);
    assertThat(startReply.getProcessInstanceKey())
        .as("a fresh PI is created, distinct from the banned holder")
        .isPositive()
        .isNotEqualTo(bannedPiKey);
  }

  @Test
  public void shouldReplyNoSubscriptionRejectedWhenStartEventSubscriptionIsAbsent() {
    // given no deployment on this partition
    // when a REQUEST arrives for an unknown processDefinitionKey
    engine.writeRecords(
        RecordToWrite.command()
            .key(SUBSCRIPTION_PLACEHOLDER_KEY)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                requestWith(BUSINESS_ID, SUBSCRIPTION_PLACEHOLDER_KEY, 424242L)));

    // then it is rejected for missing subscription (deployment-distribution race)
    final var reply =
        firstReplyCommand(MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION);
    assertThat(reply.getBusinessId()).isEqualTo(BUSINESS_ID);
    assertThat(reply.getProcessDefinitionKey()).isEqualTo(424242L);
    assertThat(reply.getProcessInstanceKey()).isEqualTo(-1L);
  }

  private MessageStartProcessInstanceRequestRecord request(
      final String businessId, final long subscriptionKey) {
    final long processDefinitionKey =
        RecordingExporter.messageStartEventSubscriptionRecords()
            .getFirst()
            .getValue()
            .getProcessDefinitionKey();
    return requestWith(businessId, subscriptionKey, processDefinitionKey);
  }

  private MessageStartProcessInstanceRequestRecord requestWith(
      final String businessId, final long subscriptionKey, final long processDefinitionKey) {
    return new MessageStartProcessInstanceRequestRecord()
        .setMessageKey(SOURCE_MESSAGE_KEY)
        .setMessageName(BufferUtil.wrapString(MESSAGE_NAME))
        .setCorrelationKey(BufferUtil.wrapString(CORRELATION_KEY))
        .setBusinessId(BufferUtil.wrapString(businessId))
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(BufferUtil.wrapString(PROCESS_ID))
        .setStartEventId(BufferUtil.wrapString(START_EVENT_ID))
        .setMessageStartEventSubscriptionKey(subscriptionKey)
        .setVariables(new UnsafeBuffer())
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private long waitForStartEventSubscriptionKey() {
    return RecordingExporter.messageStartEventSubscriptionRecords().getFirst().getKey();
  }

  private MessageStartProcessInstanceRequestRecordValue firstReplyCommand(
      final MessageStartProcessInstanceRequestIntent intent) {
    return RecordingExporter.messageStartProcessInstanceRequestRecords(intent)
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .getFirst()
        .getValue();
  }

  private void assertNoOtherReply(final MessageStartProcessInstanceRequestIntent expected) {
    for (final var other :
        new MessageStartProcessInstanceRequestIntent[] {
          MessageStartProcessInstanceRequestIntent.START,
          MessageStartProcessInstanceRequestIntent.REJECT_UNIQUENESS,
          MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION
        }) {
      if (other == expected) {
        continue;
      }
      assertThat(
              RecordingExporter.records()
                  .limit(r -> r.getIntent() == expected)
                  .filter(r -> r.getRecordType() == RecordType.COMMAND)
                  .filter(r -> r.getIntent() == other)
                  .count())
          .as("no %s reply should be produced when %s is the expected reply", other, expected)
          .isZero();
    }
  }

  private static void assertOriginalRequestPreserved(
      final MessageStartProcessInstanceRequestRecordValue value,
      final String businessId,
      final long subscriptionKey) {
    assertThat(value.getMessageKey()).isEqualTo(SOURCE_MESSAGE_KEY);
    assertThat(value.getMessageName()).isEqualTo(MESSAGE_NAME);
    assertThat(value.getCorrelationKey()).isEqualTo(CORRELATION_KEY);
    assertThat(value.getBusinessId()).isEqualTo(businessId);
    assertThat(value.getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(value.getStartEventId()).isEqualTo(START_EVENT_ID);
    assertThat(value.getMessageStartEventSubscriptionKey()).isEqualTo(subscriptionKey);
    assertThat(value.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
