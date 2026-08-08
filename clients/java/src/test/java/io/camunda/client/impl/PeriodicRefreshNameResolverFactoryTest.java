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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.NameResolver;
import io.grpc.NameResolver.Args;
import io.grpc.NameResolver.Listener2;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class PeriodicRefreshNameResolverFactoryTest {

  private static final URI TARGET_URI = URI.create("dns:///gateway.default.svc:26500");

  @Test
  void shouldDecorateResolverProducedByDelegateFactory() {
    // given
    final NameResolver delegateResolver = mock(NameResolver.class);
    final NameResolver.Factory delegateFactory = mock(NameResolver.Factory.class);
    when(delegateFactory.newNameResolver(any(URI.class), any(Args.class)))
        .thenReturn(delegateResolver);

    final ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
    mockScheduleWithFixedDelay(scheduledExecutorService);
    final Args args = mock(Args.class);
    when(args.getScheduledExecutorService()).thenReturn(scheduledExecutorService);

    final PeriodicRefreshNameResolverFactory factory =
        new PeriodicRefreshNameResolverFactory(delegateFactory, Duration.ofSeconds(30));

    // when
    final NameResolver resolver = factory.newNameResolver(TARGET_URI, args);
    resolver.start(mock(Listener2.class));

    // then — the returned resolver is a decorator that both forwards to, and schedules refreshes
    // of, the resolver produced by the delegate factory.
    verify(delegateResolver).start(any());
    verify(scheduledExecutorService)
        .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
  }

  @Test
  void shouldReturnNullWhenDelegateFactoryReturnsNull() {
    // given — grpc-java's contract allows a Factory to return null (e.g. unsupported scheme)
    final NameResolver.Factory delegateFactory = mock(NameResolver.Factory.class);
    when(delegateFactory.newNameResolver(any(URI.class), any(Args.class))).thenReturn(null);

    final PeriodicRefreshNameResolverFactory factory =
        new PeriodicRefreshNameResolverFactory(delegateFactory, Duration.ofSeconds(30));

    // when
    final NameResolver resolver = factory.newNameResolver(TARGET_URI, mock(Args.class));

    // then
    assertThat(resolver).isNull();
  }

  @Test
  void shouldDelegateDefaultScheme() {
    // given
    final NameResolver.Factory delegateFactory = mock(NameResolver.Factory.class);
    when(delegateFactory.getDefaultScheme()).thenReturn("dns");

    final PeriodicRefreshNameResolverFactory factory =
        new PeriodicRefreshNameResolverFactory(delegateFactory, Duration.ofSeconds(30));

    // when
    final String defaultScheme = factory.getDefaultScheme();

    // then
    assertThat(defaultScheme).isEqualTo("dns");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void mockScheduleWithFixedDelay(final ScheduledExecutorService executor) {
    when(executor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
        .thenReturn(mock(ScheduledFuture.class));
  }
}
