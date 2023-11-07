/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter.Listener;
import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequencerFlowControl extends AbstractLimiter<Intent> {
  private static final Logger LOG =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.transport.backpressure");
  private static final Set<? extends Intent> WHITE_LISTED_COMMANDS =
      Set.of(
          JobIntent.COMPLETE,
          JobIntent.FAIL,
          ProcessInstanceIntent.CANCEL,
          DeploymentIntent.CREATE,
          DeploymentIntent.DISTRIBUTE,
          DeploymentDistributionIntent.COMPLETE,
          CommandDistributionIntent.ACKNOWLEDGE);
  private final NavigableMap<ListenerId, Listener> responseListeners =
      new ConcurrentSkipListMap<>();

  protected SequencerFlowControl(final SequencerFlowControlBuilder builder) {
    super(builder);
  }

  @Override
  public Optional<Listener> acquire(final Intent intent) {
    if (getInflight() >= getLimit() && !WHITE_LISTED_COMMANDS.contains(intent)) {
      return createRejectedListener();
    }
    final Listener listener = createListener();
    return Optional.of(listener);
  }

  private void registerListener(final long position, final Listener listener) {
    // assumes the pair <streamId, requestId> is unique.
    responseListeners.put(new ListenerId(position), listener);
  }

  public boolean tryAcquire(final long position, final Intent context) {
    final Optional<Listener> acquired = acquire(context);
    return acquired
        .map(
            listener -> {
              registerListener(position, listener);
              return true;
            })
        .orElse(false);
  }

  public void onResponse(final long position) {
    final var listeners = responseListeners.headMap(new ListenerId(position), true);
    listeners.forEach((id, listener) -> listener.onSuccess());
    listeners.clear();
  }

  public void onIgnore(final long position) {
    final var listeners = responseListeners.headMap(new ListenerId(position), true);
    listeners.forEach((id, listener) -> listener.onIgnore());
    listeners.clear();
  }

  public int getInflightCount() {
    return getInflight();
  }

  @Override
  protected void onNewLimit(final int newLimit) {
    super.onNewLimit(newLimit);
  }

  public static SequencerFlowControlBuilder builder() {
    return new SequencerFlowControlBuilder();
  }

  public static class SequencerFlowControlBuilder
      extends AbstractLimiter.Builder<SequencerFlowControlBuilder> {

    @Override
    protected SequencerFlowControlBuilder self() {
      return this;
    }

    public SequencerFlowControl build() {
      return new SequencerFlowControl(this);
    }
  }

  record ListenerId(long position) implements Comparable<ListenerId> {

    @Override
    public int compareTo(final SequencerFlowControl.ListenerId o) {
      return Long.compare(position, o.position);
    }
  }
}
