/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.backpressure;

import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.zeebe.gateway.impl.broker.request.BrokerExecuteCommand;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import java.util.Optional;

public final class BrokerRequestLimiter extends AbstractLimiter<BrokerRequest<?>> {

  private final BackpressureMetrics metrics = new BackpressureMetrics();
  private final int partitionId;

  public BrokerRequestLimiter(final AbstractLimiter.Builder<?> builder, final int partitionId) {
    super(builder);

    this.partitionId = partitionId;
    metrics.setInflight(partitionId, 0);
    metrics.setNewLimit(partitionId, getLimit());
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public Optional<Listener> acquire(final BrokerRequest<?> request) {
    if (!(request instanceof BrokerExecuteCommand)) {
      return Optional.of(NoopListener.INSTANCE);
    }

    metrics.receivedRequest(partitionId);

    final BrokerExecuteCommand<?> command = (BrokerExecuteCommand<?>) request;
    if (isWhitelisted(command.getIntent())) {
      return Optional.of(NoopListener.INSTANCE);
    }

    if (getInflight() >= getLimit()) {
      metrics.rejectedRequest(partitionId);
      return createRejectedListener();
    }

    metrics.incInflight(partitionId);
    final Listener listener = new MetricsListener(createListener());
    return Optional.of(listener);
  }

  @Override
  protected void onNewLimit(final int newLimit) {
    super.onNewLimit(newLimit);
    metrics.setNewLimit(partitionId, newLimit);
  }

  private boolean isWhitelisted(final Intent intent) {
    return JobIntent.COMPLETE == intent;
  }

  public static final class Builder extends AbstractLimiter.Builder<Builder> {

    private int partitionId;

    public Builder withPartitionId(final int partitionId) {
      this.partitionId = partitionId;
      return self();
    }

    @Override
    protected Builder self() {
      if (partitionId < 1) {
        throw new IllegalArgumentException(
            String.format(
                "Expected partition ID to greater than or equal to 0, but %d was given",
                partitionId));
      }

      if (name == null || name.isEmpty()) {
        name = "partition-" + partitionId;
      }

      return this;
    }

    public BrokerRequestLimiter build() {
      return new BrokerRequestLimiter(this, partitionId);
    }
  }

  private static final class NoopListener implements Listener {
    private static final NoopListener INSTANCE = new NoopListener();

    @Override
    public void onSuccess() {}

    @Override
    public void onIgnore() {}

    @Override
    public void onDropped() {}
  }

  private final class MetricsListener implements Listener {
    private final Listener delegate;

    private MetricsListener(final Listener delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onSuccess() {
      delegate.onSuccess();
      metrics.decInflight(partitionId);
    }

    @Override
    public void onIgnore() {
      delegate.onIgnore();
      metrics.decInflight(partitionId);
    }

    @Override
    public void onDropped() {
      delegate.onDropped();
      metrics.decInflight(partitionId);
      metrics.dropped(partitionId);
    }
  }
}
