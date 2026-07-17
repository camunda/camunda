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
package io.camunda.client.spring.configuration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that a single {@code @JobWorker} fans out across every configured client: with two
 * clients configured, both start polling their own (physical-tenant-scoped) job-activation
 * endpoint. This exercises the multi-client lifecycle producer, the per-client event fan-out and
 * the composite-keyed job-worker registration end-to-end over REST.
 */
class MultiClientJobWorkerFanOutTest {

  @RegisterExtension
  static final WireMockExtension WM =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  @Test
  void shouldRegisterTheJobWorkerOnEveryConfiguredClient() {
    // given two clients scoped to their own physical tenants, both polling WireMock over REST
    WM.stubFor(post(urlPathMatching(".*/jobs/activation")).willReturn(okJson("{\"jobs\":[]}")));

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
        .withUserConfiguration(WorkerConfiguration.class)
        // the actuator config activates on the test classpath and wires a micrometer metrics
        // recorder used by the workers, so provide a MeterRegistry (as a real app would)
        .withBean(SimpleMeterRegistry.class)
        .withPropertyValues(
            "camunda.client.rest-address=http://localhost:" + WM.getPort(),
            "camunda.client.prefer-rest-over-grpc=true",
            // force REST polling (not the job stream) so activation calls hit WireMock
            "camunda.client.worker.defaults.stream-enabled=false",
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.finance.prefix-physical-tenant-path=true",
            "camunda.clients.risk.physical-tenant-id=risk",
            "camunda.clients.risk.prefix-physical-tenant-path=true")
        .run(
            context ->
                // then both clients poll their own physical-tenant-scoped activation endpoint
                assertThat(
                        awaitActivationPaths(
                            List.of(
                                "/physical-tenants/finance/v2/jobs/activation",
                                "/physical-tenants/risk/v2/jobs/activation"),
                            Duration.ofSeconds(15)))
                    .contains(
                        "/physical-tenants/finance/v2/jobs/activation",
                        "/physical-tenants/risk/v2/jobs/activation"));
  }

  /**
   * Polls WireMock's request journal until every expected job-activation path has been seen or the
   * timeout elapses, returning the paths seen. Uses a plain deadline loop (no {@code Thread.sleep})
   * so no extra test dependency is required.
   */
  private static Set<String> awaitActivationPaths(
      final List<String> expected, final Duration timeout) {
    final long deadline = System.nanoTime() + timeout.toNanos();
    Set<String> activationPaths = Set.of();
    while (System.nanoTime() < deadline) {
      activationPaths =
          WM.getAllServeEvents().stream()
              .map(event -> event.getRequest().getUrl())
              .filter(Objects::nonNull)
              .filter(url -> url.endsWith("/jobs/activation"))
              .collect(Collectors.toSet());
      if (activationPaths.containsAll(expected)) {
        break;
      }
      LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
    }
    return activationPaths;
  }

  @Configuration
  static class WorkerConfiguration {
    @Bean
    public FanOutWorker fanOutWorker() {
      return new FanOutWorker();
    }
  }

  static class FanOutWorker {
    @JobWorker(type = "fan-out-type", autoComplete = false)
    public void handle(final ActivatedJob job) {
      // no-op: the test only asserts that each client polls for this worker's jobs
    }
  }
}
