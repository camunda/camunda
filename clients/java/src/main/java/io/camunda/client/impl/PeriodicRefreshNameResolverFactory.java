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

import io.grpc.NameResolver;
import io.grpc.NameResolverRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * A {@link NameResolver.Factory} that decorates whatever resolver a delegate factory returns for a
 * given target URI, so that the resulting {@link NameResolver} re-resolves on a fixed interval, in
 * addition to whatever triggers grpc-java would normally rely on. The delegate's resolver is chosen
 * by grpc-java based on the target URI's scheme; in practice this is always the DNS resolver, since
 * the sole call site of this factory always builds a {@code dns:///} target.
 *
 * <p>grpc-java's DNS resolver never re-resolves on its own: it resolves once on {@link
 * NameResolver#start} and afterwards only when something external calls {@link
 * NameResolver#refresh()} -- e.g. the {@code round_robin} load balancer policy, which calls it only
 * after a subchannel transitions to a failure state. Under continuous traffic the channel never
 * goes idle either, so the resolver is never rebuilt that way. If the very first resolution returns
 * only a subset of the backend addresses -- for example a Kubernetes headless service queried while
 * a rollout is still bringing up pods -- the channel can stay pinned to that subset indefinitely,
 * even though {@code round_robin} is configured correctly. Forcing a refresh on a fixed interval
 * closes that gap.
 */
public final class PeriodicRefreshNameResolverFactory extends NameResolver.Factory {

  /**
   * Matches grpc-java's default {@code networkaddress.cache.ttl}-driven refresh throttle (see
   * {@code io.grpc.internal.DnsNameResolver}), so the periodic refresh added here does not compete
   * with, or get silently swallowed by, that throttle.
   */
  public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(30);

  private final NameResolver.Factory delegateFactory;
  private final Duration refreshInterval;

  public PeriodicRefreshNameResolverFactory(final Duration refreshInterval) {
    this(NameResolverRegistry.getDefaultRegistry().asFactory(), refreshInterval);
  }

  PeriodicRefreshNameResolverFactory(
      final NameResolver.Factory delegateFactory, final Duration refreshInterval) {
    this.delegateFactory =
        Objects.requireNonNull(delegateFactory, "delegateFactory must not be null");
    this.refreshInterval =
        Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
  }

  @Override
  public NameResolver newNameResolver(final URI targetUri, final NameResolver.Args args) {
    final NameResolver delegate = delegateFactory.newNameResolver(targetUri, args);
    if (delegate == null) {
      return null;
    }

    return new PeriodicRefreshNameResolver(
        delegate, args.getScheduledExecutorService(), refreshInterval);
  }

  @Override
  public String getDefaultScheme() {
    return delegateFactory.getDefaultScheme();
  }
}
