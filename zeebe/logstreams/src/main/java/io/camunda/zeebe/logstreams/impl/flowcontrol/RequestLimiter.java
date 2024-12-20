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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestLimiter extends AbstractLimiter<Intent> {

  private static final Set<? extends Intent> WHITE_LISTED_COMMANDS =
      Set.of(
          JobIntent.COMPLETE,
          JobIntent.FAIL,
          ProcessInstanceIntent.CANCEL,
          DeploymentIntent.CREATE,
          DeploymentIntent.DISTRIBUTE,
          DeploymentDistributionIntent.COMPLETE,
          CommandDistributionIntent.ACKNOWLEDGE);
  private static final Logger LOG =
      LoggerFactory.getLogger("io.camunda.zeebe.logstreams.impl.flowcontrol.RequestLimiter");

  private final LogStreamMetrics metrics;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private RequestLimiter(final CommandRateLimiterBuilder builder, final LogStreamMetrics metrics) {
    super(builder);
    this.metrics = metrics;
    metrics.setInflightRequests(0);
    metrics.setRequestLimit(getLimit());

    executor.scheduleAtFixedRate(
        () -> {
          LOG.info(metrics.getMetrics());
        },
        30,
        30,
        TimeUnit.SECONDS);
  }

  @Override
  public Optional<Listener> acquire(final Intent intent) {
    if (getInflight() >= getLimit() && !WHITE_LISTED_COMMANDS.contains(intent)) {
      return createRejectedListener();
    }
    final Listener listener = createListener();
    return Optional.of(listener);
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

    public RequestLimiter build(final LogStreamMetrics metrics) {
      return new RequestLimiter(this, metrics);
    }
  }
}
