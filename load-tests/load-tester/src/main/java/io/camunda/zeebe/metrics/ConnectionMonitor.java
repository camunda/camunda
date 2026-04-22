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
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared connection-readiness component used by both Starter and Worker. Retrieves the topology
 * (retrying until it succeeds) and publishes the {@code app.connected} gauge that the load-test
 * verification workflow monitors.
 */
@Component
public class ConnectionMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionMonitor.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final ZeebeClient client;
  private final AtomicInteger connected = new AtomicInteger(0);

  public ConnectionMonitor(final ZeebeClient client, final MeterRegistry registry) {
    this.client = client;
    Gauge.builder(AppMetricsDoc.CONNECTED.getName(), connected, AtomicInteger::get)
        .description(AppMetricsDoc.CONNECTED.getDescription())
        .register(registry);
  }

  /**
   * Blocks until the topology request succeeds, logging each broker/partition once. Flips the
   * {@code app.connected} gauge to 1 on success. Fails fast on authentication errors.
   */
  public void awaitAndPrintTopology() {
    while (true) {
      try {
        final var topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOG.info("Broker {} - {} ({})", b.getNodeId(), b.getAddress(), b.getVersion());
                  b.getPartitions()
                      .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        connected.set(1);
        return;
      } catch (final ClientStatusException e) {
        final var statusCode = e.getStatusCode();
        if (statusCode.equals(Code.UNAUTHENTICATED) || statusCode.equals(Code.PERMISSION_DENIED)) {
          // Fail fast through Spring's error path so @PreDestroy hooks run and tests get
          // a diagnosable stack trace. In production an uncaught exception out of this
          // thread still terminates the app via SpringApplication's default handling.
          throw new IllegalStateException(
              "Failed to retrieve topology due to authentication error; check your config", e);
        }
        THROTTLED_LOGGER.warn("Failed to retrieve topology due to client exception: ", e);
        sleep();
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to retrieve topology: ", e);
        sleep();
      }
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }
}
