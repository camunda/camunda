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
package io.camunda.client.impl.backpressure;

import io.camunda.client.api.BackpressureProfile;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * The backpressure handling of one client: the retry policy, and the adaptive concurrency limit
 * that all requests of the client share, whatever their transport.
 */
public final class BackpressureController implements AutoCloseable {

  private static final LongSupplier SYSTEM_CLOCK = () -> System.nanoTime() / 1_000_000L;
  private static final DoubleSupplier SYSTEM_RANDOM =
      () -> ThreadLocalRandom.current().nextDouble();

  private final BackpressureSettings settings;
  private final BackpressureManager manager;
  private final BackpressureScheduler scheduler;
  private final LongSupplier clockMillis;
  private final DoubleSupplier random;

  /** Lets tests control time, randomness and the passing of delays. */
  public BackpressureController(
      final BackpressureSettings settings,
      final LongSupplier clockMillis,
      final DoubleSupplier random,
      final BackpressureScheduler scheduler) {
    this.settings = settings;
    this.clockMillis = clockMillis;
    this.random = random;
    this.scheduler = scheduler;
    manager = new BackpressureManager(settings, clockMillis, scheduler);
  }

  /** Creates a controller that is not in the way: it neither retries nor limits. */
  public static BackpressureController disabled() {
    return create(BackpressureProfile.DISABLED);
  }

  public static BackpressureController create(final BackpressureProfile profile) {
    return new BackpressureController(
        BackpressureSettings.forProfile(profile),
        SYSTEM_CLOCK,
        SYSTEM_RANDOM,
        SharedTimerScheduler.INSTANCE);
  }

  /** Whether requests are retried and observed at all. */
  public boolean isEnabled() {
    return settings.isEnabled();
  }

  public BackpressureSettings settings() {
    return settings;
  }

  public BackpressureManager manager() {
    return manager;
  }

  /**
   * Starts the handling of a request. The result must be used for all attempts of the request and
   * be released once the request is done, whatever its outcome.
   *
   * @param scope how the request takes part
   * @param timeoutMillis how long the request may take altogether, waiting and retrying included;
   *     not positive for no limit
   * @return the state of the request, or {@code null} if nothing is to be done for it
   */
  public BackpressureCall newCall(final BackpressureScope scope, final long timeoutMillis) {
    return newCall(scope, timeoutMillis, settings.maxAttempts());
  }

  /**
   * Same as {@link #newCall(BackpressureScope, long)}, but with an explicit maximum number of
   * attempts, for a transport that retries on its own.
   */
  public BackpressureCall newCall(
      final BackpressureScope scope, final long timeoutMillis, final int maxAttempts) {
    if (!settings.isEnabled() || scope == BackpressureScope.UNMANAGED) {
      return null;
    }
    return new BackpressureCall(
        this, scope == BackpressureScope.MANAGED, timeoutMillis, maxAttempts);
  }

  @Override
  public void close() {
    manager.close();
  }

  long nowMillis() {
    return clockMillis.getAsLong();
  }

  double nextRandom() {
    return random.getAsDouble();
  }

  BackpressureScheduler scheduler() {
    return scheduler;
  }
}
