/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class Subscriptions {

  // UnsafeBuffer have stable equals/hashcode given it's not mutated: it must be cloned first
  private final Map<UnsafeBuffer, Subscription> subscriptions = new LinkedHashMap<>();

  public boolean contains(final DirectBuffer bpmnProcessId) {
    return subscriptions.containsKey(wrapInBufferIfNeeded(bpmnProcessId));
  }

  public void add(final MessageSubscriptionRecord subscription) {
    final var newSubscription =
        Subscription.cloned(
            subscription.getBpmnProcessIdBuffer(),
            subscription.getProcessInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getProcessDefinitionKey(),
            false);
    addSubscriptionInternal(newSubscription);
  }

  public void add(final MessageStartEventSubscriptionRecord subscription) {
    final var newSubscription =
        Subscription.cloned(
            subscription.getBpmnProcessIdBuffer(),
            subscription.getProcessInstanceKey(),
            0L,
            subscription.getProcessDefinitionKey(),
            true,
            subscription.getStartEventId());
    addSubscriptionInternal(newSubscription);
  }

  private void add(final Subscription subscription) {
    addSubscriptionInternal(Subscription.copy(subscription));
  }

  private void addSubscriptionInternal(final Subscription subscription) {
    subscriptions.put(subscription.bpmnProcessId(), subscription);
  }

  public void addAll(final Subscriptions subscriptions) {
    subscriptions.visitSubscriptions(
        (subscription) -> {
          add(subscription);
          return true;
        },
        true);
  }

  public boolean isEmpty() {
    return subscriptions.isEmpty();
  }

  public Optional<Subscription> getFirstMessageStartEventSubscription() {
    for (final Subscription subscription : subscriptions.values()) {
      if (subscription.isStartEventSubscription) {
        return Optional.of(subscription);
      }
    }
    return Optional.empty();
  }

  public boolean visitSubscriptions(final SubscriptionVisitor subscriptionConsumer) {
    return visitSubscriptions(subscriptionConsumer, false);
  }

  public boolean visitSubscriptions(
      final SubscriptionVisitor subscriptionConsumer, final boolean visitStartEvents) {
    for (final Subscription subscription : subscriptions.values()) {
      if (visitStartEvents || !subscription.isStartEventSubscription) {

        final var applied = subscriptionConsumer.apply(subscription);
        if (!applied) {
          return false;
        }
      }
    }
    return true;
  }

  private static UnsafeBuffer wrapInBufferIfNeeded(final DirectBuffer buffer) {
    if (buffer instanceof final UnsafeBuffer unsafeBuffer) {
      return unsafeBuffer;
    } else {
      return new UnsafeBuffer(buffer);
    }
  }

  public record Subscription(
      // buffer must be immutable
      UnsafeBuffer bpmnProcessId,
      long processInstanceKey,
      long elementInstanceKey,
      long processDefinitionKey,
      boolean isStartEventSubscription,
      @org.jspecify.annotations.Nullable String startEventId) {
    /* clone the bpmnProcessId buffer */
    public static Subscription cloned(
        final DirectBuffer bpmnProcessId,
        final long processInstanceKey,
        final long elementInstanceKey,
        final long processDefinitionKey,
        final boolean isStartEventSubscription) {
      return cloned(
          bpmnProcessId,
          processInstanceKey,
          elementInstanceKey,
          processDefinitionKey,
          isStartEventSubscription,
          null);
    }

    public static Subscription cloned(
        final DirectBuffer bpmnProcessId,
        final long processInstanceKey,
        final long elementInstanceKey,
        final long processDefinitionKey,
        final boolean isStartEventSubscription,
        final @org.jspecify.annotations.Nullable String startEventId) {
      return new Subscription(
          wrapInBufferIfNeeded(cloneBuffer(bpmnProcessId)),
          processInstanceKey,
          elementInstanceKey,
          processDefinitionKey,
          isStartEventSubscription,
          startEventId);
    }

    /* Copy without cloning, buffer is already cloned */
    public static Subscription copy(final Subscription subscription) {
      return new Subscription(
          subscription.bpmnProcessId(),
          subscription.processInstanceKey(),
          subscription.elementInstanceKey(),
          subscription.processDefinitionKey(),
          subscription.isStartEventSubscription,
          subscription.startEventId());
    }
  }

  @FunctionalInterface
  public interface SubscriptionVisitor {
    boolean apply(Subscription subscription);
  }
}
