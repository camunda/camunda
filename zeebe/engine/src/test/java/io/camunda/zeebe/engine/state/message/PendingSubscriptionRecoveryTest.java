/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class PendingSubscriptionRecoveryTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;

  private static List<Long> recoverInChunks(
      final PendingSubscriptionRecovery recovery, final int batchLimit) {
    final List<Long> resumeKeys = new ArrayList<>();
    long next = NO_ELEMENT_INSTANCE;
    do {
      next = recovery.recoverPending(next, batchLimit);
      resumeKeys.add(next);
    } while (next != NO_ELEMENT_INSTANCE);
    return resumeKeys;
  }

  private record Subscription(long elementInstanceKey, String messageName) {}

  @Nested
  final class ProcessMessageSubscriptions {

    // writes go through their own transient state, so only recovery fills the recovered one
    private DbProcessMessageSubscriptionState writer;
    private DbProcessMessageSubscriptionState recovered;

    @BeforeEach
    void setUp() {
      writer = newState();
      recovered = newState();
    }

    @Test
    void shouldRecoverOnlyOpeningAndClosingSubscriptions() {
      // given
      putOpening(1, "a");
      putClosing(2, "a");
      putOpened(3, "a");

      // when
      recovered.recoverPending(NO_ELEMENT_INSTANCE, Integer.MAX_VALUE);

      // then
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(1, "a"), new Subscription(2, "a"));
    }

    @Test
    void shouldReturnNoElementInstanceWhenNothingIsStored() {
      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);

      // then
      assertThat(next).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered)).isEmpty();
    }

    @Test
    void shouldNotSplitElementInstanceAcrossChunks() {
      // given
      putOpening(1, "a");
      putOpened(1, "b");
      putClosing(1, "c");
      putOpening(2, "a");

      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);

      // then
      assertThat(next).isEqualTo(2);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(1, "a"), new Subscription(1, "c"));
    }

    @Test
    void shouldLimitChunkBySubscriptionCount() {
      // given
      putOpening(1, "a");
      putOpening(1, "b");
      putOpening(2, "a");
      putOpening(2, "b");
      putOpening(3, "a");

      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 3);

      // then
      assertThat(next).isEqualTo(3);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(
              new Subscription(1, "a"),
              new Subscription(1, "b"),
              new Subscription(2, "a"),
              new Subscription(2, "b"));
    }

    @Test
    void shouldResumeAtGivenElementInstance() {
      // given
      putOpening(1, "a");
      putOpening(2, "a");
      putOpening(3, "a");

      // when
      final long next = recovered.recoverPending(2, Integer.MAX_VALUE);

      // then
      assertThat(next).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(2, "a"), new Subscription(3, "a"));
    }

    @Test
    void shouldRecoverAllPendingSubscriptionsAcrossChunks() {
      // given
      for (long elementInstanceKey = 1; elementInstanceKey <= 5; elementInstanceKey++) {
        putOpening(elementInstanceKey, "a");
        putOpened(elementInstanceKey, "b");
        putClosing(elementInstanceKey, "c");
      }

      // when
      final List<Long> resumeKeys = recoverInChunks(recovered, 1);

      // then
      assertThat(resumeKeys).containsExactly(2L, 3L, 4L, 5L, NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .hasSize(10)
          .allMatch(subscription -> !subscription.messageName().equals("b"));
    }

    @Test
    void shouldResumeAtNextElementInstanceWhenResumeKeyWasRemoved() {
      // given
      putOpening(1, "a");
      putOpening(2, "a");
      putOpening(3, "a");
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);
      writer.remove(next, wrapString("a"), TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      // when
      final long last = recovered.recoverPending(next, 1);

      // then
      assertThat(last).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(1, "a"), new Subscription(3, "a"));
    }

    @Test
    void shouldRecoverEveryTenantOfAnElementInstanceAfterResuming() {
      // given
      putOpening(1, "a", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      putOpening(2, "a", "tenant-a");
      putOpening(2, "a", "tenant-z");
      putOpening(2, "b", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      // when
      final List<Long> resumeKeys = recoverInChunks(recovered, 1);

      // then
      assertThat(resumeKeys).containsExactly(2L, NO_ELEMENT_INSTANCE);
      assertThat(pendingTenants(recovered))
          .containsExactlyInAnyOrder(
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              "tenant-a",
              "tenant-z",
              TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    void shouldRecoverAllPendingSubscriptionsWithPartitionEncodedKeys() {
      // given
      final List<Long> elementInstanceKeys =
          List.of(
              Protocol.encodePartitionId(1, 1),
              Protocol.encodePartitionId(1, 2),
              Protocol.encodePartitionId(1, 1L << 40),
              Protocol.encodePartitionId(2, 1),
              Protocol.encodePartitionId((int) Protocol.MAXIMUM_PARTITIONS - 1, 1));
      elementInstanceKeys.forEach(key -> putOpening(key, "a"));

      // when
      final List<Long> resumeKeys = recoverInChunks(recovered, 1);

      // then
      assertThat(resumeKeys)
          .containsExactlyElementsOf(
              Stream.concat(elementInstanceKeys.stream().skip(1), Stream.of(NO_ELEMENT_INSTANCE))
                  .toList());
      assertThat(pending(recovered)).hasSize(elementInstanceKeys.size());
    }

    private DbProcessMessageSubscriptionState newState() {
      return new DbProcessMessageSubscriptionState(
          zeebeDb,
          transactionContext,
          new TransientPendingSubscriptionState(),
          InstantSource.system());
    }

    private void putOpening(final long elementInstanceKey, final String messageName) {
      putOpening(elementInstanceKey, messageName, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    private void putOpening(
        final long elementInstanceKey, final String messageName, final String tenantId) {
      writer.put(elementInstanceKey, record(elementInstanceKey, messageName).setTenantId(tenantId));
    }

    private void putOpened(final long elementInstanceKey, final String messageName) {
      final var record = record(elementInstanceKey, messageName);
      writer.put(elementInstanceKey, record);
      writer.updateToOpenedState(record);
    }

    private void putClosing(final long elementInstanceKey, final String messageName) {
      final var record = record(elementInstanceKey, messageName);
      writer.put(elementInstanceKey, record);
      writer.updateToClosingState(record);
    }

    private static ProcessMessageSubscriptionRecord record(
        final long elementInstanceKey, final String messageName) {
      return new ProcessMessageSubscriptionRecord()
          .setProcessInstanceKey(100L)
          .setElementInstanceKey(elementInstanceKey)
          .setProcessDefinitionKey(200L)
          .setBpmnProcessId(wrapString("process"))
          .setElementId(wrapString("catch"))
          .setMessageName(wrapString(messageName))
          .setCorrelationKey(wrapString("correlation"))
          .setInterrupting(true)
          .setSubscriptionPartitionId(1);
    }

    private static List<String> pendingTenants(final DbProcessMessageSubscriptionState state) {
      final List<String> tenants = new ArrayList<>();
      state.visitPending(
          Long.MAX_VALUE, subscription -> tenants.add(subscription.getRecord().getTenantId()));
      return tenants;
    }

    private static List<Subscription> pending(final DbProcessMessageSubscriptionState state) {
      final List<Subscription> pending = new ArrayList<>();
      state.visitPending(
          Long.MAX_VALUE,
          subscription ->
              pending.add(
                  new Subscription(
                      subscription.getRecord().getElementInstanceKey(),
                      subscription.getRecord().getMessageName())));
      return pending;
    }
  }

  @Nested
  final class MessageSubscriptions {

    // writes go through their own transient state, so only recovery fills the recovered one
    private DbMessageSubscriptionState writer;
    private DbMessageSubscriptionState recovered;

    @BeforeEach
    void setUp() {
      writer = newState();
      recovered = newState();
    }

    @Test
    void shouldRecoverOnlyCorrelatingSubscriptions() {
      // given
      putCorrelating(1, "a");
      put(2, "a");

      // when
      recovered.recoverPending(NO_ELEMENT_INSTANCE, Integer.MAX_VALUE);

      // then
      assertThat(pending(recovered)).containsExactly(new Subscription(1, "a"));
    }

    @Test
    void shouldReturnNoElementInstanceWhenNothingIsStored() {
      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);

      // then
      assertThat(next).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered)).isEmpty();
    }

    @Test
    void shouldNotSplitElementInstanceAcrossChunks() {
      // given
      putCorrelating(1, "a");
      put(1, "b");
      putCorrelating(1, "c");
      putCorrelating(2, "a");

      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);

      // then
      assertThat(next).isEqualTo(2);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(1, "a"), new Subscription(1, "c"));
    }

    @Test
    void shouldRecoverAllPendingSubscriptionsAcrossChunks() {
      // given
      for (long elementInstanceKey = 1; elementInstanceKey <= 5; elementInstanceKey++) {
        putCorrelating(elementInstanceKey, "a");
        put(elementInstanceKey, "b");
      }

      // when
      final List<Long> resumeKeys = recoverInChunks(recovered, 1);

      // then
      assertThat(resumeKeys).containsExactly(2L, 3L, 4L, 5L, NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .hasSize(5)
          .allMatch(subscription -> subscription.messageName().equals("a"));
    }

    @Test
    void shouldLimitChunkBySubscriptionCount() {
      // given
      putCorrelating(1, "a");
      putCorrelating(1, "b");
      putCorrelating(2, "a");
      putCorrelating(2, "b");
      putCorrelating(3, "a");

      // when
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 3);

      // then
      assertThat(next).isEqualTo(3);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(
              new Subscription(1, "a"),
              new Subscription(1, "b"),
              new Subscription(2, "a"),
              new Subscription(2, "b"));
    }

    @Test
    void shouldResumeAtGivenElementInstance() {
      // given
      putCorrelating(1, "a");
      putCorrelating(2, "a");
      putCorrelating(3, "a");

      // when
      final long next = recovered.recoverPending(2, Integer.MAX_VALUE);

      // then
      assertThat(next).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(2, "a"), new Subscription(3, "a"));
    }

    @Test
    void shouldResumeAtNextElementInstanceWhenResumeKeyWasRemoved() {
      // given
      putCorrelating(1, "a");
      putCorrelating(2, "a");
      putCorrelating(3, "a");
      final long next = recovered.recoverPending(NO_ELEMENT_INSTANCE, 1);
      writer.remove(next, wrapString("a"));

      // when
      final long last = recovered.recoverPending(next, 1);

      // then
      assertThat(last).isEqualTo(NO_ELEMENT_INSTANCE);
      assertThat(pending(recovered))
          .containsExactlyInAnyOrder(new Subscription(1, "a"), new Subscription(3, "a"));
    }

    private DbMessageSubscriptionState newState() {
      return new DbMessageSubscriptionState(
          zeebeDb,
          transactionContext,
          new TransientPendingSubscriptionState(),
          InstantSource.system());
    }

    private void put(final long elementInstanceKey, final String messageName) {
      writer.put(elementInstanceKey, record(elementInstanceKey, messageName));
    }

    private void putCorrelating(final long elementInstanceKey, final String messageName) {
      final var record = record(elementInstanceKey, messageName);
      writer.put(elementInstanceKey, record);
      writer.updateToCorrelatingState(record);
    }

    private static MessageSubscriptionRecord record(
        final long elementInstanceKey, final String messageName) {
      return new MessageSubscriptionRecord()
          .setProcessInstanceKey(100L)
          .setElementInstanceKey(elementInstanceKey)
          .setProcessDefinitionKey(200L)
          .setBpmnProcessId(wrapString("process"))
          .setMessageName(wrapString(messageName))
          .setCorrelationKey(wrapString("correlation"))
          .setMessageKey(300L)
          .setInterrupting(false);
    }

    private static List<Subscription> pending(final DbMessageSubscriptionState state) {
      final List<Subscription> pending = new ArrayList<>();
      state.visitPending(
          Long.MAX_VALUE,
          subscription ->
              pending.add(
                  new Subscription(
                      subscription.getRecord().getElementInstanceKey(),
                      subscription.getRecord().getMessageName())));
      return pending;
    }
  }
}
