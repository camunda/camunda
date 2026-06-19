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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.function.Predicate;
import org.junit.Rule;
import org.junit.Test;

/**
 * Behavioral specification for using {@code businessId} as an additional, post-routing local
 * constraint on non-start (catch / boundary / intermediate) message correlation.
 *
 * <p>The asymmetric rule is load-bearing and is exercised from both sides:
 *
 * <ul>
 *   <li>A message published <b>without</b> a {@code businessId} correlates regardless of the
 *       subscription's stored value.
 *   <li>A message published <b>with</b> a {@code businessId} correlates only to subscriptions whose
 *       stored value matches exactly — both for new-message → existing-subscription correlation and
 *       for new-subscription → buffered-message correlation.
 * </ul>
 *
 * <p>Tests cover both correlation directions (publish-then-subscribe and subscribe-then-publish), a
 * multi-definition scenario where two processes share the same name/key but differ in business id
 * usage, and a pin that the value captured at OPEN time is what governs later correlation.
 */
public final class MessageCorrelationBusinessIdTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance INTERMEDIATE_CATCH_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  // --- Asymmetric rule, publish-then-subscribe is not relevant; these test
  // --- subscribe-then-publish (the "new message → existing subscription" path).

  @Test
  public void shouldCorrelateMessageWithoutBusinessIdToSubscriptionFromProcessInstanceWithOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-1")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when
    engine.message().withName("message").withCorrelationKey("order-1").publish();

    // then
    assertProcessCompleted(processInstanceKey);
  }

  @Test
  public void shouldCorrelateMessageWithoutBusinessIdToSubscriptionWithoutOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-2")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when
    engine.message().withName("message").withCorrelationKey("order-2").publish();

    // then
    assertProcessCompleted(processInstanceKey);
  }

  @Test
  public void shouldCorrelateMessageWithBusinessIdToSubscriptionWithMatchingOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-3")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-3")
        .withBusinessId("biz-42")
        .publish();

    // then
    assertProcessCompleted(processInstanceKey);
  }

  @Test
  public void shouldNotCorrelateMessageWithBusinessIdToSubscriptionWithDifferentOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-4")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when a message with a different businessId is published with a TTL of 0 so the
    // engine processes the publish-and-discard cycle deterministically.
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-4")
        .withBusinessId("other-biz")
        .withTimeToLive(0L)
        .publish();

    // then the message lifecycle completes (EXPIRED) without ever correlating the subscription.
    assertNoCorrelationUpToMessageExpiry(processInstanceKey, "message", "order-4");
  }

  @Test
  public void shouldNotCorrelateMessageWithBusinessIdToSubscriptionWithoutOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-5")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-5")
        .withBusinessId("biz-42")
        .withTimeToLive(0L)
        .publish();

    // then
    assertNoCorrelationUpToMessageExpiry(processInstanceKey, "message", "order-5");
  }

  // --- Buffered-message path: publish first, subscribe later.

  @Test
  public void shouldCorrelateBufferedMessageWithMatchingBusinessIdToNewSubscription() {
    // given a message published before any matching subscription exists
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-6")
        .withBusinessId("biz-42")
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // when a process instance with the matching businessId opens the subscription
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-6")
            .create();

    // then the buffered message is correlated and the process completes
    assertProcessCompleted(processInstanceKey);
  }

  @Test
  public void shouldNotCorrelateBufferedMessageWithBusinessIdToSubscriptionWithDifferentOne() {
    // given
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-7")
        .withBusinessId("biz-42")
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // when a process instance with a different businessId opens the subscription
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("other-biz")
            .withVariable("key", "order-7")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // then the buffered message is not consumed; we use a separate TTL=0 sentinel publish on an
    // unrelated correlation key as a deterministic boundary to assert "no correlation happened
    // up to this point".
    engine
        .message()
        .withName("sentinel")
        .withCorrelationKey("sentinel-key-1")
        .withTimeToLive(0L)
        .publish();
    assertNoCorrelationUpToMessageExpiry(processInstanceKey, "sentinel", "sentinel-key-1");
  }

  // --- Multi-definition: one published message vs subscriptions from two different process
  // --- definitions with different businessId values.

  @Test
  public void shouldOnlyCorrelateToSubscriptionsWithMatchingBusinessIdAcrossDefinitions() {
    // given two distinct process definitions subscribing to the same message name + correlation
    // key, plus three instances with different business ids
    final var matchingProcessId = "matching-" + PROCESS_ID;
    final var nonMatchingProcessId = "non-matching-" + PROCESS_ID;
    engine
        .deployment()
        .withXmlResource(processWithIntermediateCatch(matchingProcessId))
        .withXmlResource(processWithIntermediateCatch(nonMatchingProcessId))
        .deploy();

    final long matchingPi =
        engine
            .processInstance()
            .ofBpmnProcessId(matchingProcessId)
            .withBusinessId("biz-42")
            .withVariable("key", "order-8")
            .create();
    final long nonMatchingPi =
        engine
            .processInstance()
            .ofBpmnProcessId(nonMatchingProcessId)
            .withBusinessId("other-biz")
            .withVariable("key", "order-8")
            .create();
    awaitSubscriptionCreated(matchingPi);
    awaitSubscriptionCreated(nonMatchingPi);

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-8")
        .withBusinessId("biz-42")
        .publish();

    // then only the matching PI completes; the non-matching one keeps waiting (the matching PI's
    // completion is itself the deterministic terminal we bound the negative assertion on).
    assertProcessCompleted(matchingPi);
    assertNoCorrelationForProcessInstance(nonMatchingPi, matchingPi);
  }

  // --- Pinning: businessId captured at OPEN time governs correlation; the subscription's stored
  // --- value is what's matched, not anything resolved at correlation time from PI state.

  @Test
  public void shouldUseBusinessIdCapturedAtSubscriptionOpenToFilterCorrelation() {
    // given a subscription was opened from a PI with businessId "biz-42"
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-9")
            .create();
    final var subscriptionCreated = awaitSubscriptionCreated(processInstanceKey);

    // then the subscription on the message partition carries that businessId; later correlation
    // decisions are made against this stored value, not by re-reading PI state across partitions.
    assertThat(subscriptionCreated.getValue().getBusinessId()).isEqualTo("biz-42");

    // and an exactly matching publish correlates against that stored value
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-9")
        .withBusinessId("biz-42")
        .publish();
    assertProcessCompleted(processInstanceKey);
  }

  // --- Pinning: the process instance's businessId (captured at OPEN) is carried onto the
  // --- ProcessMessageSubscription:CORRELATED event, so the correlated-message read path can expose
  // --- it for non-start correlations. The correlate command itself carries no businessId.

  @Test
  public void shouldRecordProcessInstanceBusinessIdOnCatchCorrelation() {
    // given a PI with businessId "biz-42" waiting on an intermediate catch event
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withBusinessId("biz-42")
            .withVariable("key", "order-10")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when a message without a businessId correlates (asymmetric rule: it still correlates)
    engine.message().withName("message").withCorrelationKey("order-10").publish();

    // then the CORRELATED event carries the subscribing PI's businessId, not the message's (empty)
    final var correlated =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(correlated.getValue().getBusinessId()).isEqualTo("biz-42");
  }

  @Test
  public void shouldRecordEmptyBusinessIdOnCatchCorrelationWhenProcessInstanceHasNone() {
    // given a PI without a businessId waiting on an intermediate catch event
    engine.deployment().withXmlResource(INTERMEDIATE_CATCH_PROCESS).deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-11")
            .create();
    awaitSubscriptionCreated(processInstanceKey);

    // when a message correlates
    engine.message().withName("message").withCorrelationKey("order-11").publish();

    // then the CORRELATED event carries an empty businessId (no regression for the none path)
    final var correlated =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(correlated.getValue().getBusinessId()).isEmpty();
  }

  private static BpmnModelInstance processWithIntermediateCatch(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .intermediateCatchEvent("receive-message")
        .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
        .endEvent()
        .done();
  }

  private static Record<MessageSubscriptionRecordValue> awaitSubscriptionCreated(
      final long processInstanceKey) {
    return RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private static void assertProcessCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.PROCESS)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .isNotNull();
  }

  /**
   * Assert that no CORRELATING/CORRELATED records for {@code processInstanceKey} appear in the
   * record stream up to the EXPIRED of the specific message identified by {@code messageName} +
   * {@code correlationKey}. The caller is responsible for publishing that message (typically with
   * TTL=0) so its EXPIRED is a deterministic terminal we can bound on. Matching the specific
   * message — rather than the first EXPIRED of any message — keeps the helper honest if multiple
   * messages happen to expire in the same test stream.
   */
  private static void assertNoCorrelationUpToMessageExpiry(
      final long processInstanceKey, final String messageName, final String correlationKey) {
    final Predicate<Record<?>> messageExpired =
        r ->
            r.getIntent() == MessageIntent.EXPIRED
                && r.getValue() instanceof final MessageRecordValue v
                && messageName.equals(v.getName())
                && correlationKey.equals(v.getCorrelationKey());
    final boolean correlated =
        RecordingExporter.records()
            .limit(messageExpired::test)
            .messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(MessageSubscriptionIntent.CORRELATED)
            .exists();
    assertThat(correlated).isFalse();
    final boolean correlating =
        RecordingExporter.records()
            .limit(messageExpired::test)
            .messageSubscriptionRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(MessageSubscriptionIntent.CORRELATING)
            .exists();
    assertThat(correlating).isFalse();
  }

  /**
   * Assert that the {@code targetProcessInstanceKey} never had a correlation, using the {@code
   * boundaryProcessInstanceKey}'s completion (a deterministic positive terminal) as the upper bound
   * of the observed record stream.
   */
  private static void assertNoCorrelationForProcessInstance(
      final long targetProcessInstanceKey, final long boundaryProcessInstanceKey) {
    final boolean correlated =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                        && r.getValue() instanceof final ProcessInstanceRecordValue v
                        && v.getProcessInstanceKey() == boundaryProcessInstanceKey
                        && v.getBpmnElementType() == BpmnElementType.PROCESS)
            .messageSubscriptionRecords()
            .withProcessInstanceKey(targetProcessInstanceKey)
            .withIntent(MessageSubscriptionIntent.CORRELATED)
            .exists();
    assertThat(correlated).isFalse();
  }
}
