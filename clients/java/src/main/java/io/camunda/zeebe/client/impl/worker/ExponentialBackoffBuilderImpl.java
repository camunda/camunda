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
import io.zeebe.client.api.worker.ExponentialBackoffBuilder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class ExponentialBackoffBuilderImpl implements ExponentialBackoffBuilder {

  private static final long DEFAULT_MAX_DELAY = TimeUnit.SECONDS.toMillis(5);
  private static final long DEFAULT_MIN_DELAY = TimeUnit.MILLISECONDS.toMillis(50);
  private static final double DEFAULT_MULTIPLIER = 1.6;
  private static final double DEFAULT_JITTER = 0.1;
  private static final Random DEFAULT_RANDOM = new Random();

  private long maxDelay;
  private long minDelay;
  private double backoffFactor;
  private double jitterFactor;
  private Random random;

  public ExponentialBackoffBuilderImpl() {
    maxDelay = DEFAULT_MAX_DELAY;
    minDelay = DEFAULT_MIN_DELAY;
    backoffFactor = DEFAULT_MULTIPLIER;
    jitterFactor = DEFAULT_JITTER;
    random = DEFAULT_RANDOM;
  }

  @Override
  public ExponentialBackoffBuilder maxDelay(final long maxDelay) {
    this.maxDelay = maxDelay;
    return this;
  }

  @Override
  public ExponentialBackoffBuilder minDelay(final long minDelay) {
    this.minDelay = minDelay;
    return this;
  }

  @Override
  public ExponentialBackoffBuilder backoffFactor(final double backoffFactor) {
    this.backoffFactor = backoffFactor;
    return this;
  }

  @Override
  public ExponentialBackoffBuilder jitterFactor(final double jitterFactor) {
    this.jitterFactor = jitterFactor;
    return this;
  }

  @Override
  public ExponentialBackoffBuilder random(final Random random) {
    this.random = random;
    return this;
  }

  @Override
  public BackoffSupplier build() {
    return new ExponentialBackoff(maxDelay, minDelay, backoffFactor, jitterFactor, random);
  }
}
