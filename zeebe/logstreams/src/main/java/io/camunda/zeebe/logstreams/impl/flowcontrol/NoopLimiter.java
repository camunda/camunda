/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import java.util.Optional;

final class NoopLimiter implements Limiter<Void> {

  private final NoopListener listener = new NoopListener();

  public NoopLimiter(final AppenderMetrics metrics) {
    metrics.setInflightLimit(-1);
  }

  @Override
  public Optional<Listener> acquire(final Void context) {
    return Optional.of(listener);
  }

  private static final class NoopListener implements Limiter.Listener {
    @Override
    public void onSuccess() {}

    @Override
    public void onIgnore() {}

    @Override
    public void onDropped() {}
  }
}
