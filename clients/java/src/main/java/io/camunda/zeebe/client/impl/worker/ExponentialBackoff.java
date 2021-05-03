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
package io.zeebe.client.impl.worker;

import io.zeebe.client.api.worker.BackoffSupplier;
import java.util.Random;

/**
 * An implementation of {@link BackoffSupplier} which uses a simple formula, multiplying the
 * previous delay with an increasing multiplier and adding some jitter to avoid multiple clients
 * polling at the same time even with back off.
 *
 * <p>The next delay is calculated as:
 *
 * <pre> max(min(maxDelay, currentDelay * backoffFactor), minDelay) + (rand(0.0, 1.0) *
 * (currentDelay * jitterFactor) + (currentDelay * -jitterFactor)) </pre>
 */
public final class ExponentialBackoff implements BackoffSupplier {

  private final long maxDelay;
  private final long minDelay;
  private final double backoffFactor;
  private final double jitterFactor;
  private final Random random;

  public ExponentialBackoff(
      final long maxDelay,
      final long minDelay,
      final double backoffFactor,
      final double jitterFactor,
      final Random random) {
    this.maxDelay = maxDelay;
    this.minDelay = minDelay;
    this.backoffFactor = backoffFactor;
    this.jitterFactor = jitterFactor;
    this.random = random;
  }

  @Override
  public long supplyRetryDelay(final long currentRetryDelay) {
    final double delay = Math.max(Math.min(maxDelay, currentRetryDelay * backoffFactor), minDelay);
    final double jitter = computeJitter(delay);
    return Math.round(delay + jitter);
  }

  private double computeJitter(final double value) {
    final double minFactor = value * -jitterFactor;
    final double maxFactor = value * jitterFactor;

    return (random.nextDouble() * (maxFactor - minFactor)) + minFactor;
  }
}
