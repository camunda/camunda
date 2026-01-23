/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.AtomicKeyGenerator;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageStreamProcessorTest {

  private static final int PARTITION_ID = 1;
  private static final EngineConfiguration DEFAULT_ENGINE_CONFIGURATION = new EngineConfiguration();
  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  @Rule public final StreamProcessorRule rule = new StreamProcessorRule(PARTITION_ID);

  private SubscriptionCommandSender spySubscriptionCommandSender;
  private InterPartitionCommandSender mockInterpartitionCommandSender;
  private CommandDistributionBehavior spyCommandDistributionBehavior;

  @Before
  public void setup() {
    mockInterpartitionCommandSender = mock(InterPartitionCommandSender.class);
    final var keyGenerator = new AtomicKeyGenerator(PARTITION_ID);
    final var mockDistributionState = mock(DistributionState.class);
    final var mockProcessingResultBuilder = mock(ProcessingResultBuilder.class);
    final var mockEventAppliers = mock(EventAppliers.class);
    final var writers = new Writers(() -> mockProcessingResultBuilder, mockEventAppliers);
    writers.setKeyValidator(keyGenerator);
    spySubscriptionCommandSender =
        spy(new SubscriptionCommandSender(PARTITION_ID, mockInterpartitionCommandSender));
    spySubscriptionCommandSender.setWriters(writers);
    final var routingInfo = RoutingInfo.forStaticPartitions(1);
    spyCommandDistributionBehavior =
        spy(
            new CommandDistributionBehavior(
                mockDistributionState,
                writers,
                PARTITION_ID,
                routingInfo,
                mockInterpartitionCommandSender,
                mock(DistributionMetrics.class)));

    rule.startTypedStreamProcessor(
        (typedRecordProcessors, processingContext) -> {
          final var processingState = processingContext.getProcessingState();
          final var scheduledTaskState = processingContext.getScheduledTaskStateFactory();
          final var mockAuthCheckBehavior = mock(AuthorizationCheckBehavior.class);
          when(mockAuthCheckBehavior.isAuthorizedOrInternalCommand(any(AuthorizationRequest.class)))
              .thenReturn(Either.right(null));
          MessageEventProcessors.addMessageProcessors(
              PARTITION_ID,
              mock(BpmnBehaviors.class),
              typedRecordProcessors,
              processingState,
              scheduledTaskState,
              spySubscriptionCommandSender,
              processingContext.getWriters(),
              DEFAULT_ENGINE_CONFIGURATION,
              FeatureFlags.createDefault(),
              spyCommandDistributionBehavior,
              InstantSource.system(),
              mockAuthCheckBehavior,
              routingInfo);
          return typedRecordProcessors;
        });
  }

  @Test
  public void shouldRejectDuplicatedOpenMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();

    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    // when
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    // then
    final Record<MessageSubscriptionRecord> rejection =
        awaitAndGet(
            () -> rule.events().onlyMessageSubscriptionRecords().onlyRejections().findFirst());

    assertThat(rejection.getIntent()).isEqualTo(MessageSubscriptionIntent.CREATE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);

    verify(spySubscriptionCommandSender, timeout(5_000).times(2))
        .openProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            any(),
            anyBoolean(),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldRetryToCorrelateMessageSubscriptionAfterPublishedMessage() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);
    waitUntil(
        () -> rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).exists());

    // when

    // then
    // we need to enhance the clock on retry, since the next task is scheduled only after the first
    // executed
    Awaitility.await("retry correlation")
        .untilAsserted(
            () -> {
              rule.getClock()
                  .addTime(
                      PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_CHECK_INTERVAL.plus(
                          PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_TIMEOUT));
              verify(mockInterpartitionCommandSender, timeout(100).atLeast(2))
                  .sendCommand(
                      eq(0),
                      eq(ValueType.PROCESS_MESSAGE_SUBSCRIPTION),
                      eq(ProcessMessageSubscriptionIntent.CORRELATE),
                      any());
            });
  }

  @Test
  public void shouldRetryToCorrelateMessageSubscriptionAfterOpenedSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .exists());

    // when

    // then
    // we need to enhance the clock on retry, since the next task is scheduled only after the first
    // executed
    Awaitility.await("retry correlation")
        .untilAsserted(
            () -> {
              rule.getClock()
                  .addTime(
                      PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_CHECK_INTERVAL.plus(
                          PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_TIMEOUT));
              verify(mockInterpartitionCommandSender, timeout(100).times(2))
                  .sendCommand(
                      eq(0),
                      eq(ValueType.PROCESS_MESSAGE_SUBSCRIPTION),
                      eq(ProcessMessageSubscriptionIntent.CORRELATE),
                      any());
            });
  }

  @Test
  public void shouldRejectCorrelateIfMessageSubscriptionClosed() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);
    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .exists());

    // when
    rule.writeCommand(MessageSubscriptionIntent.DELETE, subscription);
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, subscription);

    // then
    final Record<MessageSubscriptionRecord> rejection =
        awaitAndGet(
            () -> rule.events().onlyMessageSubscriptionRecords().onlyRejections().findFirst());

    assertThat(rejection.getIntent()).isEqualTo(MessageSubscriptionIntent.CORRELATE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectDuplicatedCloseMessageSubscription() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .exists());

    // when
    rule.writeCommand(MessageSubscriptionIntent.DELETE, subscription);
    rule.writeCommand(MessageSubscriptionIntent.DELETE, subscription);

    // then
    final Record<MessageSubscriptionRecord> rejection =
        awaitAndGet(
            () -> rule.events().onlyMessageSubscriptionRecords().onlyRejections().findFirst());

    assertThat(rejection.getIntent()).isEqualTo(MessageSubscriptionIntent.DELETE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    // cannot verify messageName buffer since it is a view around another buffer which is changed
    // by the time we perform the verification.
    verify(spySubscriptionCommandSender, timeout(5_000).times(2))
        .closeProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            any(DirectBuffer.class),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void shouldNotCorrelateNewMessagesIfSubscriptionNotCorrelatable() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();

    // when
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);
    waitUntil(
        () -> rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).exists());
    rule.writeCommand(MessageIntent.PUBLISH, message);

    // then
    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();

    verify(spySubscriptionCommandSender, timeout(5_000).times(1))
        .correlateProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            any(),
            any(),
            eq(messageKey),
            any(),
            any(),
            eq(DEFAULT_TENANT));
  }

  @Test
  public void shouldCorrelateNewMessagesIfSubscriptionIsReusable() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription();
    final MessageRecord message = message();
    subscription.setInterrupting(false);

    // when
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);
    rule.writeCommand(MessageIntent.PUBLISH, message);

    final var firstMessage =
        awaitAndGet(
            () ->
                rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).findFirst());

    rule.writeCommand(
        MessageSubscriptionIntent.CORRELATE, subscription.setMessageKey(firstMessage.getKey()));
    rule.writeCommand(MessageIntent.PUBLISH, message);

    // then
    waitUntil(
        () ->
            rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).limit(2).count()
                == 2);
    final long lastMessageKey =
        rule.events()
            .onlyMessageRecords()
            .withIntent(MessageIntent.PUBLISHED)
            .skip(1)
            .getFirst()
            .getKey();

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            any(),
            eq(firstMessage.getKey()),
            any(),
            any(),
            eq(DEFAULT_TENANT));

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            any(),
            eq(lastMessageKey),
            any(),
            any(),
            eq(DEFAULT_TENANT));
  }

  @Test
  public void shouldCorrelateMultipleMessagesOneBeforeOpenOneAfter() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription().setInterrupting(false);
    final MessageRecord first = message().setVariables(asMsgPack("foo", "bar"));
    final MessageRecord second = message().setVariables(asMsgPack("foo", "baz"));

    // when
    rule.writeCommand(MessageIntent.PUBLISH, first);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .exists());

    final var firstMessageRecord =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst();

    rule.writeCommand(
        MessageSubscriptionIntent.CORRELATE,
        subscription.setMessageKey(firstMessageRecord.getKey()));
    rule.writeCommand(MessageIntent.PUBLISH, second);

    // then
    assertAllMessagesReceived(subscription);
  }

  @Test
  public void shouldCorrelateMultipleMessagesTwoBeforeOpen() {
    // given
    final MessageSubscriptionRecord subscription = messageSubscription().setInterrupting(false);
    final MessageRecord first = message().setVariables(asMsgPack("foo", "bar"));
    final MessageRecord second = message().setVariables(asMsgPack("foo", "baz"));

    // when
    rule.writeCommand(MessageIntent.PUBLISH, first);
    rule.writeCommand(MessageIntent.PUBLISH, second);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, subscription);

    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .exists());

    final var firstMessage =
        awaitAndGet(
            () ->
                rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).findFirst());
    rule.writeCommand(
        MessageSubscriptionIntent.CORRELATE, subscription.setMessageKey(firstMessage.getKey()));

    // then
    assertAllMessagesReceived(subscription);
  }

  @Test
  public void shouldCorrelateToFirstSubscriptionAfterRejection() {
    // given
    final MessageRecord message = message();
    final MessageSubscriptionRecord firstSubscription =
        messageSubscription().setElementInstanceKey(5L);
    final MessageSubscriptionRecord secondSubscription =
        messageSubscription().setElementInstanceKey(10L);

    // when
    rule.writeCommand(MessageIntent.PUBLISH, message);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, firstSubscription);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, secondSubscription);
    waitUntil(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(MessageSubscriptionIntent.CREATED)
                .filter(
                    r ->
                        r.getValue().getElementInstanceKey()
                            == secondSubscription.getElementInstanceKey())
                .exists());

    final long messageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    firstSubscription.setMessageKey(messageKey);
    rule.writeCommand(MessageSubscriptionIntent.REJECT, firstSubscription);

    // then
    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(firstSubscription.getProcessInstanceKey()),
            eq(firstSubscription.getElementInstanceKey()),
            eq(firstSubscription.getProcessDefinitionKey()),
            eq(firstSubscription.getBpmnProcessIdBuffer()),
            any(DirectBuffer.class),
            eq(messageKey),
            any(DirectBuffer.class),
            eq(firstSubscription.getCorrelationKeyBuffer()),
            eq(DEFAULT_TENANT));

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(secondSubscription.getProcessInstanceKey()),
            eq(secondSubscription.getElementInstanceKey()),
            eq(secondSubscription.getProcessDefinitionKey()),
            eq(secondSubscription.getBpmnProcessIdBuffer()),
            any(DirectBuffer.class),
            eq(messageKey),
            any(DirectBuffer.class),
            eq(secondSubscription.getCorrelationKeyBuffer()),
            eq(DEFAULT_TENANT));
  }

  private void assertAllMessagesReceived(final MessageSubscriptionRecord subscription) {
    waitUntil(
        () ->
            rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).limit(2).count()
                == 2);
    final long firstMessageKey =
        rule.events().onlyMessageRecords().withIntent(MessageIntent.PUBLISHED).getFirst().getKey();
    final long lastMessageKey =
        rule.events()
            .onlyMessageRecords()
            .withIntent(MessageIntent.PUBLISHED)
            .skip(1)
            .getFirst()
            .getKey();

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            eq(subscription.getMessageNameBuffer()),
            eq(firstMessageKey),
            any(),
            eq(subscription.getCorrelationKeyBuffer()),
            eq(DEFAULT_TENANT));

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(subscription.getProcessInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getProcessDefinitionKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            eq(subscription.getMessageNameBuffer()),
            eq(lastMessageKey),
            any(),
            eq(subscription.getCorrelationKeyBuffer()),
            eq(DEFAULT_TENANT));
  }

  private MessageSubscriptionRecord messageSubscription() {
    final MessageSubscriptionRecord subscription = new MessageSubscriptionRecord();
    subscription
        .setProcessInstanceKey(1L)
        .setElementInstanceKey(2L)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(wrapString("process"))
        .setMessageKey(-1L)
        .setMessageName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setInterrupting(true);

    return subscription;
  }

  private MessageRecord message() {
    final MessageRecord message = new MessageRecord();
    message
        .setName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setTimeToLive(Duration.ofSeconds(10).toMillis())
        .setVariables(asMsgPack("orderId", "order-123"));

    return message;
  }

  private <T extends RecordValue> Record<T> awaitAndGet(
      final Supplier<Optional<Record<T>>> supplier) {
    waitUntil(() -> supplier.get().isPresent());
    return supplier.get().get();
  }
}
