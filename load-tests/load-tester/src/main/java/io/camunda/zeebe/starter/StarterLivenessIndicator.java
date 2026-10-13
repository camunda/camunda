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
package io.camunda.zeebe.starter;

import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Liveness indicator of the starter. Reports DOWN when no start request succeeded for longer than
 * the configured age, so that Kubernetes restarts the pod. An answer that signals backpressure
 * counts as a success, as the cluster is responding. Every other failure does not, whatever its
 * cause: rejected credentials, a stopped request scheduler and an unreachable cluster all end in
 * the same state, a starter that creates no process instances.
 */
@Component("starterLiveness")
@Profile("starter")
public class StarterLivenessIndicator implements HealthIndicator {

  private final Duration maxNoSuccessAge;
  private final InstantSource clock;
  private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
  private final AtomicBoolean finished = new AtomicBoolean();

  @Autowired
  public StarterLivenessIndicator(final LoadTesterProperties properties) {
    this(properties.getStarter(), InstantSource.system());
  }

  StarterLivenessIndicator(final StarterProperties properties, final InstantSource clock) {
    maxNoSuccessAge = properties.getLivenessMaxNoSuccessAge();
    this.clock = clock;
  }

  /** Starts the clock once the starter is connected to the cluster. */
  void recordStarted() {
    lastSuccess.compareAndSet(null, clock.instant());
  }

  /** Records the answer of a request; {@code error} is {@code null} if it succeeded. */
  void recordResult(final Throwable error) {
    if (error == null || isBackpressure(error)) {
      lastSuccess.set(clock.instant());
    }
  }

  /** Records that the starter completed its run, after which liveness stays UP. */
  void recordFinished() {
    finished.set(true);
  }

  @Override
  public Health health() {
    final var success = lastSuccess.get();
    if (!finished.get() && success != null) {
      final var age = Duration.between(success, clock.instant());
      if (age.compareTo(maxNoSuccessAge) > 0) {
        return Health.down().withDetail("lastSuccessAge", age.toString()).build();
      }
    }
    return Health.up().build();
  }

  private static boolean isBackpressure(final Throwable error) {
    return Status.fromThrowable(error).getCode() == Status.Code.RESOURCE_EXHAUSTED;
  }
}
