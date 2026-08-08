/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * A {@link NameResolver} decorator that forwards every call to a delegate resolver, and
 * additionally forces {@link NameResolver#refresh()} on a fixed interval for as long as the
 * resolver is running. See {@link PeriodicRefreshNameResolverFactory} for why this is needed.
 *
 * <p>Also logs the resolved addresses on every (re-)resolution, at {@code INFO} when the resolved
 * set changed since the last resolution and at {@code DEBUG} otherwise, so that "did we ever
 * resolve down to a single address" can be answered from logs after the fact without needing a
 * log-level change in production.
 */
final class PeriodicRefreshNameResolver extends NameResolver {

  private final NameResolver delegate;
  private final ScheduledExecutorService scheduledExecutorService;
  private final Duration refreshInterval;
  private final Logger logger;

  private volatile ScheduledFuture<?> refreshTask;
  private volatile Set<EquivalentAddressGroup> lastResolvedAddresses = Collections.emptySet();

  PeriodicRefreshNameResolver(
      final NameResolver delegate,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration refreshInterval) {
    this(delegate, scheduledExecutorService, refreshInterval, Loggers.LOGGER);
  }

  PeriodicRefreshNameResolver(
      final NameResolver delegate,
      final ScheduledExecutorService scheduledExecutorService,
      final Duration refreshInterval,
      final Logger logger) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.scheduledExecutorService =
        Objects.requireNonNull(
            scheduledExecutorService, "scheduledExecutorService must not be null");
    this.refreshInterval =
        Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
    this.logger = Objects.requireNonNull(logger, "logger must not be null");
  }

  @Override
  public String getServiceAuthority() {
    return delegate.getServiceAuthority();
  }

  @Override
  public void start(final Listener2 listener) {
    delegate.start(new LoggingListener(listener));

    final long intervalMillis = refreshInterval.toMillis();
    refreshTask =
        scheduledExecutorService.scheduleWithFixedDelay(
            delegate::refresh, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void refresh() {
    delegate.refresh();
  }

  @Override
  public void shutdown() {
    final ScheduledFuture<?> task = refreshTask;
    if (task != null) {
      task.cancel(false);
    }
    delegate.shutdown();
  }

  private void logResolution(final List<EquivalentAddressGroup> addresses) {
    final Set<EquivalentAddressGroup> resolvedAddresses = new HashSet<>(addresses);
    if (resolvedAddresses.equals(lastResolvedAddresses)) {
      logger.debug(
          "gRPC client-side load balancing: DNS refresh returned the same {} address(es): {}",
          addresses.size(),
          addresses);
      return;
    }

    lastResolvedAddresses = resolvedAddresses;
    logger.info(
        "gRPC client-side load balancing: DNS resolved {} address(es): {}",
        addresses.size(),
        addresses);
  }

  /** Forwards to the real listener, logging every resolution result as it passes through. */
  private final class LoggingListener extends Listener2 {

    private final Listener2 delegateListener;

    private LoggingListener(final Listener2 delegateListener) {
      this.delegateListener = delegateListener;
    }

    @Override
    public void onResult(final ResolutionResult resolutionResult) {
      logResolution(resolutionResult.getAddresses());
      delegateListener.onResult(resolutionResult);
    }

    @Override
    public void onError(final Status error) {
      logger.warn("gRPC client-side load balancing: DNS resolution failed: {}", error);
      delegateListener.onError(error);
    }
  }
}
