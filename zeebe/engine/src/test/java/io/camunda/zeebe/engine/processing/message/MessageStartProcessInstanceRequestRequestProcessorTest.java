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
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the {@code P_B}-side processor of the cross-partition message-start handshake in
 * isolation: a directly-written {@link MessageStartProcessInstanceRequestIntent#REQUEST} command is
 * consumed and translated into one of the four reply commands (START, REJECT_UNIQUENESS,
 * REJECT_NO_SUBSCRIPTION, REJECT_EXPIRED). The routing flip that drives this handshake from a
 * published message lands in a later commit; until then, this is the only end-to-end exercise of
 * the processor.
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
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

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

  @Test
  public void shouldReplyExpiredRejectedWhenDeadlinePassedAndTtlPositive() {
    // given a deployed process whose businessId is free, so without the guard the request WOULD
    // start a PI — proving the guard short-circuits before dedup lookup and live evaluation
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();

    // when a REQUEST arrives whose deadline has already passed and that carried a positive TTL
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey)
                    .setMessageDeadline(1L)
                    .setMessageTtl(1_000L)));

    // then the TTL-gated expiry guard refuses it: REJECT_EXPIRED reply, no PI, no dedup-based START
    final var reply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED);
    assertOriginalRequestPreserved(reply, BUSINESS_ID, subscriptionKey);
    assertThat(reply.getProcessInstanceKey())
        .as("expiry rejections do not carry a PI key")
        .isEqualTo(-1L);

    final long startedPis =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(startedPis).as("no PI is activated for an expired request").isZero();
  }

  @Test
  public void shouldStartWhenDeadlinePassedButTtlIsZero() {
    // given a deployed process with a free businessId
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();

    // when a past-deadline REQUEST arrives but its TTL is 0 (fire-and-forget): a TTL=0 message
    // always arrives past its deadline, so the guard must NOT reject it (documented TTL=0
    // first-arrival activation)
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey).setMessageDeadline(1L).setMessageTtl(0L)));

    // then it falls through to live evaluation and starts a PI, replying START (not REJECT_EXPIRED)
    final var startReply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.START);
    assertThat(startReply.getProcessInstanceKey()).isPositive();
  }

  @Test
  public void shouldTreatExactDeadlineBoundaryAsExpired() {
    // Pins the guard's boundary semantics as inclusive (messageDeadline <= now, not <): a request
    // whose deadline is exactly the current clock value counts as expired. Pinning the clock makes
    // the processor's clock.millis() stable so an exact-equality deadline can be asserted
    // deterministically — with a live clock the comparison would race the wall clock.
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    engine.getClock().pinCurrentTime();
    final long now = engine.getClock().getCurrentTimeInMillis();

    // when a REQUEST arrives whose messageDeadline is EXACTLY now (positive TTL)
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey)
                    .setMessageDeadline(now)
                    .setMessageTtl(1_000L)));

    // then the inclusive boundary refuses it: REJECT_EXPIRED reply, no PI activated. A strict `<`
    // guard would instead start a PI here, so this asserts the `<=` choice directly.
    final var reply = firstReplyCommand(MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED);
    assertThat(reply.getProcessInstanceKey()).isEqualTo(-1L);

    final long startedPis =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(startedPis)
        .as("messageDeadline == now must be treated as expired (inclusive <=)")
        .isZero();
  }

  @Test
  public void shouldReplyNoSubscriptionRejectedWhenTargetDefinitionIsDraining() {
    // given - a deployed process whose definition is then marked DRAINING
    final var metadata =
        engine
            .deployment()
            .withXmlResource(MESSAGE_START_PROCESS)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    drain(metadata);

    // when - a REQUEST arrives for the draining definition
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                requestWith(BUSINESS_ID, subscriptionKey, metadata.getProcessDefinitionKey())));

    // then - it is rejected as "no subscription" and no PI is activated
    final var reply =
        firstReplyCommand(MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION);
    assertThat(reply.getProcessDefinitionKey()).isEqualTo(metadata.getProcessDefinitionKey());
    assertThat(reply.getProcessInstanceKey()).isEqualTo(-1L);

    // no STARTED event is written for a phantom instance and no PROCESS is activated
    final long startedEvents =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent()
                            == MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION)
            .filter(
                r ->
                    r.getRecordType() == RecordType.EVENT
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.STARTED)
            .count();
    assertThat(startedEvents).as("no dedup/STARTED entry for a draining definition").isZero();

    final long activatedProcesses =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent()
                            == MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activatedProcesses).as("no PI is activated for a draining definition").isZero();

    assertNoOtherReply(MessageStartProcessInstanceRequestIntent.REJECT_NO_SUBSCRIPTION);
  }

  /**
   * Puts the given process definition into the {@code DRAINING} state. Since no processor writes
   * the {@code DRAINING} event yet, the event is injected onto the log while the engine is stopped
   * so it is applied to state on the next start (replay). TODO(#56978): drive draining via a real
   * {@code RESOURCE_DELETION.DELETE} once that change lands, and remove this injection helper.
   */
  private void drain(final ProcessMetadataValue metadata) {
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
                    .setTenantId(metadata.getTenantId())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
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
