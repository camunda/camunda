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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.NameResolver.Listener2;
import io.grpc.NameResolver.ResolutionResult;
import io.grpc.Status;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

@Execution(ExecutionMode.CONCURRENT)
final class PeriodicRefreshNameResolverTest {

  private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);

  private final NameResolver delegate = mock(NameResolver.class);
  private final ScheduledExecutorService scheduledExecutorService =
      mock(ScheduledExecutorService.class);
  private final Logger logger = mock(Logger.class);
  private final PeriodicRefreshNameResolver resolver =
      new PeriodicRefreshNameResolver(delegate, scheduledExecutorService, REFRESH_INTERVAL, logger);

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldRefreshDelegateRepeatedlyOnSchedule() {
    // given — a fake scheduler whose scheduled task we invoke ourselves, standing in for
    // grpc-java's channel-owned scheduled executor actually firing it on an interval.
    final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
    final ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(scheduledExecutorService.scheduleWithFixedDelay(
            taskCaptor.capture(), anyLong(), anyLong(), any()))
        .thenReturn((ScheduledFuture) scheduledFuture);

    // when
    resolver.start(mock(Listener2.class));
    final Runnable scheduledRefresh = taskCaptor.getValue();
    scheduledRefresh.run();
    scheduledRefresh.run();
    scheduledRefresh.run();

    // then — the delegate is refreshed once per firing, repeatedly, not just once at start
    verify(delegate, times(3)).refresh();
    verify(scheduledExecutorService)
        .scheduleWithFixedDelay(
            any(Runnable.class),
            eq(REFRESH_INTERVAL.toMillis()),
            eq(REFRESH_INTERVAL.toMillis()),
            eq(TimeUnit.MILLISECONDS));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldStopSchedulingRefreshesAfterShutdown() {
    // given
    final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
    when(scheduledExecutorService.scheduleWithFixedDelay(
            any(Runnable.class), anyLong(), anyLong(), any()))
        .thenReturn((ScheduledFuture) scheduledFuture);
    resolver.start(mock(Listener2.class));

    // when
    resolver.shutdown();

    // then — the scheduled refresh task is cancelled and the delegate resolver is shut down
    verify(scheduledFuture).cancel(false);
    verify(delegate).shutdown();
  }

  @Test
  void shouldForwardServiceAuthorityToDelegate() {
    // given
    when(delegate.getServiceAuthority()).thenReturn("gateway.default.svc");

    // when
    final String authority = resolver.getServiceAuthority();

    // then
    assertThat(authority).isEqualTo("gateway.default.svc");
    verify(delegate).getServiceAuthority();
  }

  @Test
  void shouldForwardExplicitRefreshToDelegate() {
    // when — e.g. round_robin's Helper#refreshNameResolution() calling refresh() directly
    resolver.refresh();

    // then
    verify(delegate).refresh();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void shouldLogAtInfoOnlyWhenResolvedAddressesChange() {
    // given
    mockScheduling();
    final Listener2 channelListener = mock(Listener2.class);
    final ArgumentCaptor<Listener2> listenerCaptor = ArgumentCaptor.forClass(Listener2.class);
    resolver.start(channelListener);
    verify(delegate).start(listenerCaptor.capture());
    final Listener2 wrappedListener = listenerCaptor.getValue();

    final ResolutionResult firstResolution = resolutionResultOf(address(26500));
    final ResolutionResult sameResolutionAgain = resolutionResultOf(address(26500));
    final ResolutionResult changedResolution = resolutionResultOf(address(26500), address(26501));

    // when — resolved addresses stay the same on the second resolution, then change on the third
    wrappedListener.onResult(firstResolution);
    wrappedListener.onResult(sameResolutionAgain);
    wrappedListener.onResult(changedResolution);

    // then — INFO only fires when the resolved set actually changes (twice: initial + change),
    // never on the unchanged refresh; the unchanged refresh is still visible at DEBUG.
    verify(logger, times(2)).info(anyString(), any(), any());
    verify(logger, times(1)).debug(anyString(), any(), any());
    verify(channelListener, times(3)).onResult(any());
  }

  @Test
  void shouldLogAndForwardResolutionErrors() {
    // given
    mockScheduling();
    final Listener2 channelListener = mock(Listener2.class);
    final ArgumentCaptor<Listener2> listenerCaptor = ArgumentCaptor.forClass(Listener2.class);
    resolver.start(channelListener);
    verify(delegate).start(listenerCaptor.capture());
    final Listener2 wrappedListener = listenerCaptor.getValue();
    final Status error = Status.UNAVAILABLE.withDescription("DNS lookup failed");

    // when
    wrappedListener.onError(error);

    // then
    verify(logger).warn(anyString(), eq(error));
    verify(channelListener).onError(error);
    verify(logger, never()).info(anyString(), any(), any());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void mockScheduling() {
    final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
    when(scheduledExecutorService.scheduleWithFixedDelay(
            any(Runnable.class), anyLong(), anyLong(), any()))
        .thenReturn((ScheduledFuture) scheduledFuture);
  }

  private static ResolutionResult resolutionResultOf(final EquivalentAddressGroup... addresses) {
    return ResolutionResult.newBuilder().setAddresses(Arrays.asList(addresses)).build();
  }

  private static EquivalentAddressGroup address(final int port) {
    return new EquivalentAddressGroup(new InetSocketAddress("127.0.0.1", port));
  }
}
