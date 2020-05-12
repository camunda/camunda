package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import java.time.Duration;

public abstract class AbstractDelayedHealthIndicatorProperties {

  private Duration maxDowntime = Duration.ofMinutes(5);

  protected abstract Duration getDefaultMaxDowntime();

  public Duration getMaxDowntime() {
    return maxDowntime;
  }

  public void setMaxDowntime(final Duration maxDowntime) {
    if (requireNonNull(maxDowntime).toMillis() < 0) {
      throw new IllegalArgumentException("MaxDowntime must be >= 0");
    }

    this.maxDowntime = maxDowntime;
  }
}
