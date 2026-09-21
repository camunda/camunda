/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.AtomicKeyGenerator;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.util.StreamProcessorRule;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Stream-level tests for {@link MessageSubscriptionRejectProcessor#findSubscriptionToCorrelate},
 * the message-partition side of the stale-generation reroute: once a stale REJECT releases the
 * correlation lock a superseded subscription (K1) held, the buffered message must find its way to
 * the live replacement (K2) instead of being lost until its TTL expires.
 *
 * <p>K1 and K2 are plain, competing subscriptions for the same message name/correlation key; the
 * "staleness" is simulated by never acknowledging K1's correlation before writing a REJECT for it.
 * See <a href="https://github.com/camunda/camunda/issues/61099">#61099</a>.
 */
public final class MessageSubscriptionRejectProcessorTest {

  private static final int PARTITION_ID = 1;
  private static final EngineConfiguration DEFAULT_ENGINE_CONFIGURATION = new EngineConfiguration();
  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;

  @Rule public final StreamProcessorRule rule = new StreamProcessorRule(PARTITION_ID);

  private SubscriptionCommandSender spySubscriptionCommandSender;

  @Before
  public void setup() {
    final var mockInterpartitionCommandSender = mock(InterPartitionCommandSender.class);
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
    final var commandDistributionBehavior =
        spy(
            new CommandDistributionBehavior(
                mockDistributionState,
                writers,
                PARTITION_ID,
                routingInfo,
                mockInterpartitionCommandSender,
                mock(DistributionMetrics.class),
                mock(ControllableStreamClock.class)));

    rule.startTypedStreamProcessor(
        (typedRecordProcessors, processingContext) -> {
          final var processingState = processingContext.getProcessingState();
          final var scheduledTaskState = processingContext.getScheduledTaskStateFactory();
          final var mockBpmnBehaviors = mock(BpmnBehaviors.class);
          final var mockVariableBehavior = mock(VariableBehavior.class);
          when(mockVariableBehavior.validateVariables(any())).thenReturn(Either.right(null));
          when(mockBpmnBehaviors.variableBehavior()).thenReturn(mockVariableBehavior);
          MessageEventProcessors.addMessageProcessors(
              PARTITION_ID,
              mockBpmnBehaviors,
              typedRecordProcessors,
              processingState,
              scheduledTaskState,
              spySubscriptionCommandSender,
              processingContext.getWriters(),
              DEFAULT_ENGINE_CONFIGURATION,
              FeatureFlags.createDefault(),
              commandDistributionBehavior,
              InstantSource.system(),
              routingInfo,
              mock(AuthorizationCheckPort.class),
              mock(LazyTokenClaimsConverter.class),
              mock(EngineSecurityConfig.class),
              new MessageCorrelationMetrics(new SimpleMeterRegistry()));
          return typedRecordProcessors;
        });
  }

  @Test
  public void shouldRerouteStaleRejectedMessageToReplacementSubscription() {
    // given
    final MessageSubscriptionRecord k1 = subscription().setElementInstanceKey(1L);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k1);
    rule.writeCommand(MessageIntent.PUBLISH, message());
    final long messageKey =
        awaitFirstByElement(k1.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING)
            .getValue()
            .getMessageKey();

    final MessageSubscriptionRecord k2 = subscription().setElementInstanceKey(2L);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k2);
    awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CREATED);

    // when
    rule.writeCommand(MessageSubscriptionIntent.REJECT, k1.setMessageKey(messageKey));

    // then
    final var correlating =
        awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING);
    assertThat(correlating.getValue().getMessageKey()).isEqualTo(messageKey);

    verify(spySubscriptionCommandSender, timeout(5_000))
        .correlateProcessMessageSubscription(
            eq(k2.getProcessInstanceKey()),
            eq(k2.getElementInstanceKey()),
            eq(k2.getProcessDefinitionKey()),
            eq(k2.getBpmnProcessIdBuffer()),
            eq(k2.getMessageNameBuffer()),
            eq(messageKey),
            any(),
            eq(k2.getCorrelationKeyBuffer()),
            eq(DEFAULT_TENANT),
            anyLong());
  }

  @Test
  public void shouldNotReCorrelateReplacementForDuplicateStaleReject() {
    // given
    final MessageSubscriptionRecord k1 = subscription().setElementInstanceKey(1L);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k1);
    rule.writeCommand(MessageIntent.PUBLISH, message());
    final long messageKey =
        awaitFirstByElement(k1.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING)
            .getValue()
            .getMessageKey();

    final MessageSubscriptionRecord k2 = subscription().setElementInstanceKey(2L);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k2);
    awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CREATED);

    rule.writeCommand(MessageSubscriptionIntent.REJECT, k1.setMessageKey(messageKey));
    awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING);

    // when
    rule.writeCommand(MessageSubscriptionIntent.REJECT, k1);

    // then
    waitUntil(
        () ->
            rule.events()
                    .onlyMessageSubscriptionRecords()
                    .withIntent(MessageSubscriptionIntent.REJECTED)
                    .count()
                >= 2);
    final long correlatingEventsForK2 =
        rule.events()
            .onlyMessageSubscriptionRecords()
            .withIntent(MessageSubscriptionIntent.CORRELATING)
            .filter(r -> r.getValue().getElementInstanceKey() == k2.getElementInstanceKey())
            .count();
    assertThat(correlatingEventsForK2).isEqualTo(1);
  }

  @Test
  public void shouldRerouteEarlierLockedMessageEvenAfterReplacementSkippedAheadToLaterOne() {
    // given
    final MessageSubscriptionRecord k1 = subscription().setElementInstanceKey(1L);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k1);
    rule.writeCommand(MessageIntent.PUBLISH, message());
    final long m1Key =
        awaitFirstByElement(k1.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING)
            .getValue()
            .getMessageKey();

    final MessageSubscriptionRecord k2 =
        subscription().setElementInstanceKey(2L).setInterrupting(false);
    rule.writeCommand(MessageSubscriptionIntent.CREATE, k2);
    rule.writeCommand(MessageIntent.PUBLISH, message());
    final var correlatingM2 =
        awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATING);
    final long m2Key = correlatingM2.getValue().getMessageKey();
    assertThat(m2Key).isNotEqualTo(m1Key);

    // acknowledge K2's correlation of M2, so K2 is no longer marked as correlating
    rule.writeCommand(MessageSubscriptionIntent.CORRELATE, k2.setMessageKey(m2Key));
    awaitFirstByElement(k2.getElementInstanceKey(), MessageSubscriptionIntent.CORRELATED);

    // when
    rule.writeCommand(MessageSubscriptionIntent.REJECT, k1.setMessageKey(m1Key));

    // then
    final var secondCorrelating =
        awaitAndGet(
            () ->
                rule.events()
                    .onlyMessageSubscriptionRecords()
                    .withIntent(MessageSubscriptionIntent.CORRELATING)
                    .filter(r -> r.getValue().getElementInstanceKey() == k2.getElementInstanceKey())
                    .skip(1)
                    .findFirst());
    assertThat(secondCorrelating.getValue().getMessageKey()).isEqualTo(m1Key);
  }

  private Record<MessageSubscriptionRecord> awaitFirstByElement(
      final long elementInstanceKey, final MessageSubscriptionIntent intent) {
    return awaitAndGet(
        () ->
            rule.events()
                .onlyMessageSubscriptionRecords()
                .withIntent(intent)
                .filter(r -> r.getValue().getElementInstanceKey() == elementInstanceKey)
                .findFirst());
  }

  private MessageSubscriptionRecord subscription() {
    return new MessageSubscriptionRecord()
        .setProcessInstanceKey(1L)
        .setProcessDefinitionKey(3L)
        .setBpmnProcessId(wrapString("process"))
        .setMessageKey(-1L)
        .setMessageName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setInterrupting(true);
  }

  private MessageRecord message() {
    return new MessageRecord()
        .setName(wrapString("order canceled"))
        .setCorrelationKey(wrapString("order-123"))
        .setTimeToLive(Duration.ofMinutes(5).toMillis());
  }

  private <T extends RecordValue> Record<T> awaitAndGet(
      final Supplier<Optional<Record<T>>> supplier) {
    waitUntil(() -> supplier.get().isPresent());
    return supplier.get().get();
  }
}
