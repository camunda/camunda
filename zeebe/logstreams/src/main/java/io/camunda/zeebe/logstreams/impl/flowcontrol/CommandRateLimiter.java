/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandRateLimiter extends AbstractLimiter<Intent> {

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
  private final Map<ListenerId, Listener> responseListeners = new ConcurrentHashMap<>();
  private final LogStreamMetrics metrics;

  private CommandRateLimiter(
      final CommandRateLimiterBuilder builder, final LogStreamMetrics metrics) {
    super(builder);
    this.metrics = metrics;
    metrics.setInflightRequests(0);
    metrics.setRequestLimit(getLimit());
  }

  @Override
  public Optional<Listener> acquire(final Intent intent) {
    if (getInflight() >= getLimit() && !WHITE_LISTED_COMMANDS.contains(intent)) {
      return createRejectedListener();
    }
    final Listener listener = createListener();
    return Optional.of(listener);
  }

  private void registerListener(final int streamId, final long requestId, final Listener listener) {
    // assumes the pair <streamId, requestId> is unique.
    responseListeners.put(new ListenerId(streamId, requestId), listener);
  }

  public boolean tryAcquire(final int streamId, final long requestId, final Intent context) {
    final Optional<Listener> acquired = acquire(context);
    return acquired
        .map(
            listener -> {
              registerListener(streamId, requestId, listener);
              metrics.increaseInflightRequests();
              return true;
            })
        .orElse(false);
  }

  public void onResponse(final int streamId, final long requestId) {
    final Listener listener = responseListeners.remove(new ListenerId(streamId, requestId));
    if (listener != null) {
      try {
        listener.onSuccess();
      } catch (final IllegalArgumentException e) {
        LOG.warn(
            "Could not register request RTT (likely caused by clock problems). Consider using the 'fixed' backpressure algorithm.",
            e);
        listener.onIgnore();
      }

      metrics.decreaseInflightRequests();
    } else {
      // Ignore this message, if it happens immediately after failover. It can happen when a request
      // committed by the old leader is processed by the new leader.
      LOG.trace(
          "Expected to have a rate limiter listener for request-{}-{}, but none found. (This can happen during fail over.)",
          streamId,
          requestId);
    }
  }

  public void onIgnore(final int streamId, final long requestId) {
    final Listener listener = responseListeners.remove(new ListenerId(streamId, requestId));
    if (listener != null) {
      listener.onIgnore();
      metrics.decreaseInflightRequests();
    }
  }

  public int getInflightCount() {
    return getInflight();
  }

  @Override
  protected void onNewLimit(final int newLimit) {
    super.onNewLimit(newLimit);
    metrics.setRequestLimit(newLimit);
  }

  public static CommandRateLimiterBuilder builder() {
    return new CommandRateLimiterBuilder();
  }

  public static class CommandRateLimiterBuilder
      extends AbstractLimiter.Builder<CommandRateLimiterBuilder> {

    @Override
    protected CommandRateLimiterBuilder self() {
      return this;
    }

    public CommandRateLimiter build(final LogStreamMetrics metrics) {
      return new CommandRateLimiter(this, metrics);
    }
  }

  static class ListenerId {
    private final int streamId;
    private final long requestId;

    ListenerId(final int streamId, final long requestId) {
      this.streamId = streamId;
      this.requestId = requestId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(streamId, requestId);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ListenerId that = (ListenerId) o;
      return streamId == that.streamId && requestId == that.requestId;
    }
  }
}
