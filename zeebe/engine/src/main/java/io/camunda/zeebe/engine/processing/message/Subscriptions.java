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
import io.camunda.zeebe.util.collection.Reusable;
import io.camunda.zeebe.util.collection.ReusableObjectList;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class Subscriptions {

  private final ReusableObjectList<Subscription> subscriptions =
      new ReusableObjectList<>(Subscription::new);

  public void clear() {
    subscriptions.clear();
  }

  public boolean contains(final DirectBuffer bpmnProcessId) {
    for (final Subscription subscription : subscriptions) {
      if (subscription.getBpmnProcessId().equals(bpmnProcessId)) {
        return true;
      }
    }
    return false;
  }

  public void add(final MessageSubscriptionRecord subscription) {
    final var newSubscription = subscriptions.add();
    newSubscription.setBpmnProcessId(cloneBuffer(subscription.getBpmnProcessIdBuffer()));
    newSubscription.processInstanceKey = subscription.getProcessInstanceKey();
    newSubscription.elementInstanceKey = subscription.getElementInstanceKey();
  }

  public void add(final MessageStartEventSubscriptionRecord subscription) {
    final var newSubscription = subscriptions.add();
    newSubscription.setBpmnProcessId(cloneBuffer(subscription.getBpmnProcessIdBuffer()));
    newSubscription.isStartEventSubscription = true;
    newSubscription.processInstanceKey = subscription.getProcessInstanceKey();
  }

  private void add(final Subscription subscription) {
    final var newSubscription = subscriptions.add();
    newSubscription.setBpmnProcessId(subscription.getBpmnProcessId());
    newSubscription.processInstanceKey = subscription.processInstanceKey;
    newSubscription.elementInstanceKey = subscription.elementInstanceKey;
    newSubscription.isStartEventSubscription = subscription.isStartEventSubscription;
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
    return subscriptions.size() <= 0;
  }

  public Optional<Subscription> getFirstMessageStartEventSubscription() {
    for (final Subscription subscription : subscriptions) {
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
    for (final Subscription subscription : subscriptions) {
      if (visitStartEvents || !subscription.isStartEventSubscription) {

        final var applied = subscriptionConsumer.apply(subscription);
        if (!applied) {
          return false;
        }
      }
    }
    return true;
  }

  @FunctionalInterface
  public interface SubscriptionVisitor {
    boolean apply(Subscription subscription);
  }

  public final class Subscription implements Reusable {

    private final MutableDirectBuffer bpmnProcessId = new ExpandableArrayBuffer();
    private final DirectBuffer bufferView = new UnsafeBuffer(bpmnProcessId);
    private int bufferLength = 0;

    private long processInstanceKey;
    private long elementInstanceKey;
    private boolean isStartEventSubscription;

    @Override
    public void reset() {
      bufferLength = 0;
      bufferView.wrap(0, 0);

      processInstanceKey = -1L;
      elementInstanceKey = -1L;
      isStartEventSubscription = false;
    }

    public DirectBuffer getBpmnProcessId() {
      return bufferView;
    }

    private void setBpmnProcessId(final DirectBuffer bpmnProcessId) {
      bufferLength = bpmnProcessId.capacity();
      bpmnProcessId.getBytes(0, this.bpmnProcessId, 0, bufferLength);
      bufferView.wrap(this.bpmnProcessId, 0, bufferLength);
    }

    public long getProcessInstanceKey() {
      return processInstanceKey;
    }

    public long getElementInstanceKey() {
      return elementInstanceKey;
    }

    public boolean isStartEventSubscription() {
      return isStartEventSubscription;
    }
  }
}
