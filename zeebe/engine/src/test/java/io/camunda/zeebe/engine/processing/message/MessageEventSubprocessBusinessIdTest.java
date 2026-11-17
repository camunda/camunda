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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;

/**
 * Event-subprocess arm of the non-start {@code businessId} filter. A message-start event inside an
 * event subprocess does <b>not</b> create a root process instance — it correlates into an already
 * running instance via an instance-scoped {@link
 * io.camunda.zeebe.protocol.record.ValueType#PROCESS_MESSAGE_SUBSCRIPTION} (the same record type as
 * an intermediate catch or boundary event), never a definition-scoped {@code
 * MessageStartEventSubscription}. Therefore the start-event {@code businessId} <em>uniqueness</em>
 * constraint must not apply here: {@code businessId} is a plain catch-style filter only.
 *
 * <p>These tests run with {@code businessIdUniquenessEnabled} <b>on</b> so the no-uniqueness
 * guarantee is actually tested and not trivially true: a second top-level {@code
 * CreateProcessInstance} with the same {@code businessId} for the same definition would be rejected
 * {@code ALREADY_EXISTS} (pinned in {@code CreateProcessInstanceBusinessIdUniquenessTest}); a
 * message that resolves to an event-subprocess start carrying that very same in-use {@code
 * businessId} is not.
 */
public final class MessageEventSubprocessBusinessIdTest {

  private static final String PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final String EVENT_SUBPROCESS_START = "event-subprocess-start";

  // A non-interrupting event subprocess started by a message event, on a process whose main flow
  // parks on a service task so the instance stays active. The event-subprocess subscription is
  // opened (carrying the instance's businessId captured at OPEN time) as soon as the instance
  // activates, exactly like a catch/boundary subscription.
  private static final BpmnModelInstance EVENT_SUBPROCESS_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .eventSubProcess(
              "event-subprocess",
              s ->
                  s.startEvent(EVENT_SUBPROCESS_START)
                      .interrupting(false)
                      .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key"))
                      .serviceTask("sub-task", t -> t.zeebeJobType("sub"))
                      .endEvent())
          .startEvent()
          .serviceTask("main-task", t -> t.zeebeJobType("main"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Test
  public void shouldTriggerEventSubprocessStartForMessageWithInUseBusinessIdWithoutUniqueness() {
    // given an active instance holding businessId "biz-42" with an event-subprocess message start.
    // A second top-level create with this businessId for this definition would be rejected
    // ALREADY_EXISTS (CreateProcessInstanceBusinessIdUniquenessTest); the event-subprocess
    // correlation below must not be.
    final long processInstanceKey = deployAndAwaitSubscription("biz-42", "order-1");

    // when a message carrying that same in-use businessId is published
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("order-1")
        .withBusinessId("biz-42")
        .publish();

    // then the event subprocess start fires on the already-running instance — the observable proof
    // that the uniqueness machinery was not engaged: a message carrying an in-use businessId
    // correlated into the existing instance instead of being treated as a guarded new root start.
    // The CORRELATED subscription carries the instance's businessId.
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(EVENT_SUBPROCESS_START)
                .getFirst())
        .isNotNull();
    assertThat(correlatedRecord(processInstanceKey).getValue().getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  public void shouldNotTriggerEventSubprocessStartForMessageWithDifferentBusinessId() {
    // given
    final long processInstanceKey = deployAndAwaitSubscription("biz-42", "order-2");

    // when a message with a different businessId is published with TTL=0 for a deterministic
    // EXPIRED terminal
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey("order-2")
        .withBusinessId("other-biz")
        .withTimeToLive(0L)
        .publish();

    // then the event subprocess start never fires
    final Predicate<Record<?>> messageExpired =
        r ->
            r.getIntent() == MessageIntent.EXPIRED
                && r.getValue() instanceof final MessageRecordValue v
                && MESSAGE_NAME.equals(v.getName())
                && "order-2".equals(v.getCorrelationKey());
    final boolean triggered =
        RecordingExporter.records()
            .limit(messageExpired::test)
            .processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(EVENT_SUBPROCESS_START)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .exists();
    assertThat(triggered).isFalse();
  }

  private long deployAndAwaitSubscription(final String businessId, final String correlationKey) {
    engine.deployment().withXmlResource(EVENT_SUBPROCESS_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId(businessId)
            .withVariable("key", correlationKey)
            .create();
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    return processInstanceKey;
  }

  private static Record<ProcessMessageSubscriptionRecordValue> correlatedRecord(
      final long processInstanceKey) {
    return RecordingExporter.processMessageSubscriptionRecords(
            ProcessMessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }
}
