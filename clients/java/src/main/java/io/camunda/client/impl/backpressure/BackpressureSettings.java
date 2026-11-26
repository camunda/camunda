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

/**
 * The tunables of the client-side backpressure handling: the retry policy of a single request and
 * the parameters of the adaptive concurrency limiter shared by all requests of one client.
 *
 * <p>The defaults and the values of the profiles are those of the Camunda TypeScript SDK.
 */
public final class BackpressureSettings {

  // retry of a single request
  private final boolean enabled;
  private final int maxAttempts;
  private final long baseDelayMs;
  private final long maxDelayMs;

  // adaptive concurrency limiter
  private final boolean gating;
  private final int initialMax;
  private final double softFactor;
  private final double severeFactor;
  private final long recoveryIntervalMs;
  private final int recoveryStep;
  private final long decayQuietMs;
  private final int floor;
  private final int severeThreshold;
  private final int maxWaiters;
  private final double healthyRecoveryMultiplier;
  private final long unlimitedAfterHealthyMs;
  private final long backoffInitialMs;
  private final long backoffMaxMs;
  private final double backoffEscalate;

  private BackpressureSettings(final Builder b) {
    enabled = b.enabled;
    maxAttempts = b.maxAttempts;
    baseDelayMs = b.baseDelayMs;
    maxDelayMs = b.maxDelayMs;
    gating = b.gating;
    initialMax = b.initialMax;
    softFactor = b.softFactor;
    severeFactor = b.severeFactor;
    recoveryIntervalMs = b.recoveryIntervalMs;
    recoveryStep = b.recoveryStep;
    decayQuietMs = b.decayQuietMs;
    floor = b.floor;
    severeThreshold = b.severeThreshold;
    maxWaiters = b.maxWaiters;
    healthyRecoveryMultiplier = b.healthyRecoveryMultiplier;
    unlimitedAfterHealthyMs = b.unlimitedAfterHealthyMs;
    backoffInitialMs = b.backoffInitialMs;
    backoffMaxMs = b.backoffMaxMs;
    backoffEscalate = b.backoffEscalate;
  }

  /** Returns the settings of the given profile; {@code null} stands for the default profile. */
  public static BackpressureSettings forProfile(final BackpressureProfile profile) {
    final BackpressureProfile effective = profile == null ? BackpressureProfile.BALANCED : profile;
    final Builder builder = new Builder();
    switch (effective) {
      case DISABLED:
        builder.enabled(false).gating(false);
        break;
      case LEGACY:
        builder.gating(false);
        break;
      case CONSERVATIVE:
        builder
            .initialMax(12)
            .softFactor(0.6)
            .severeFactor(0.4)
            .recoveryIntervalMs(1200)
            .recoveryStep(1)
            .decayQuietMs(2500)
            .floor(1)
            .severeThreshold(2)
            .maxWaiters(500)
            .healthyRecoveryMultiplier(1.3)
            .unlimitedAfterHealthyMs(60_000);
        break;
      case AGGRESSIVE:
        builder
            .initialMax(24)
            .softFactor(0.8)
            .severeFactor(0.6)
            .recoveryIntervalMs(800)
            .recoveryStep(2)
            .decayQuietMs(1500)
            .floor(2)
            .severeThreshold(4)
            .maxWaiters(2000)
            .healthyRecoveryMultiplier(2.0)
            .unlimitedAfterHealthyMs(15_000);
        break;
      case BALANCED:
      default:
        break;
    }
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Whether requests are retried and observed at all; {@code false} restores the old behavior. */
  public boolean isEnabled() {
    return enabled;
  }

  /** Whether the concurrency of requests is limited, as opposed to only retrying them. */
  public boolean isGating() {
    return gating;
  }

  /** Total attempts per request, including the first one. */
  public int maxAttempts() {
    return maxAttempts;
  }

  public long baseDelayMs() {
    return baseDelayMs;
  }

  public long maxDelayMs() {
    return maxDelayMs;
  }

  int initialMax() {
    return initialMax;
  }

  double softFactor() {
    return softFactor;
  }

  double severeFactor() {
    return severeFactor;
  }

  long recoveryIntervalMs() {
    return recoveryIntervalMs;
  }

  int recoveryStep() {
    return recoveryStep;
  }

  long decayQuietMs() {
    return decayQuietMs;
  }

  int floor() {
    return floor;
  }

  int severeThreshold() {
    return severeThreshold;
  }

  int maxWaiters() {
    return maxWaiters;
  }

  double healthyRecoveryMultiplier() {
    return healthyRecoveryMultiplier;
  }

  long unlimitedAfterHealthyMs() {
    return unlimitedAfterHealthyMs;
  }

  long backoffInitialMs() {
    return backoffInitialMs;
  }

  long backoffMaxMs() {
    return backoffMaxMs;
  }

  double backoffEscalate() {
    return backoffEscalate;
  }

  /** The values start out as those of the {@link BackpressureProfile#BALANCED} profile. */
  public static final class Builder {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private long baseDelayMs = 100;
    private long maxDelayMs = 2000;
    private boolean gating = true;
    private int initialMax = 16;
    private double softFactor = 0.7;
    private double severeFactor = 0.5;
    private long recoveryIntervalMs = 1000;
    private int recoveryStep = 1;
    private long decayQuietMs = 2000;
    private int floor = 1;
    private int severeThreshold = 3;
    private int maxWaiters = 1000;
    private double healthyRecoveryMultiplier = 1.5;
    private long unlimitedAfterHealthyMs = 30_000;
    private long backoffInitialMs = 25;
    private long backoffMaxMs = 2000;
    private double backoffEscalate = 2.0;

    private Builder() {}

    public Builder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder gating(final boolean gating) {
      this.gating = gating;
      return this;
    }

    public Builder maxAttempts(final int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    public Builder baseDelayMs(final long baseDelayMs) {
      this.baseDelayMs = baseDelayMs;
      return this;
    }

    public Builder maxDelayMs(final long maxDelayMs) {
      this.maxDelayMs = maxDelayMs;
      return this;
    }

    public Builder initialMax(final int initialMax) {
      this.initialMax = initialMax;
      return this;
    }

    public Builder softFactor(final double softFactor) {
      this.softFactor = softFactor;
      return this;
    }

    public Builder severeFactor(final double severeFactor) {
      this.severeFactor = severeFactor;
      return this;
    }

    public Builder recoveryIntervalMs(final long recoveryIntervalMs) {
      this.recoveryIntervalMs = recoveryIntervalMs;
      return this;
    }

    public Builder recoveryStep(final int recoveryStep) {
      this.recoveryStep = recoveryStep;
      return this;
    }

    public Builder decayQuietMs(final long decayQuietMs) {
      this.decayQuietMs = decayQuietMs;
      return this;
    }

    public Builder floor(final int floor) {
      this.floor = floor;
      return this;
    }

    public Builder severeThreshold(final int severeThreshold) {
      this.severeThreshold = severeThreshold;
      return this;
    }

    public Builder maxWaiters(final int maxWaiters) {
      this.maxWaiters = maxWaiters;
      return this;
    }

    public Builder healthyRecoveryMultiplier(final double healthyRecoveryMultiplier) {
      this.healthyRecoveryMultiplier = healthyRecoveryMultiplier;
      return this;
    }

    public Builder unlimitedAfterHealthyMs(final long unlimitedAfterHealthyMs) {
      this.unlimitedAfterHealthyMs = unlimitedAfterHealthyMs;
      return this;
    }

    public Builder backoffInitialMs(final long backoffInitialMs) {
      this.backoffInitialMs = backoffInitialMs;
      return this;
    }

    public Builder backoffMaxMs(final long backoffMaxMs) {
      this.backoffMaxMs = backoffMaxMs;
      return this;
    }

    public Builder backoffEscalate(final double backoffEscalate) {
      this.backoffEscalate = backoffEscalate;
      return this;
    }

    public BackpressureSettings build() {
      return new BackpressureSettings(this);
    }
  }
}
