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
package io.camunda.client.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.event.CamundaClientCreatedEvent;
import io.camunda.client.metrics.JobWorkerMetricsFactory.JobWorkerMetricsFactoryContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class MicrometerJobWorkerMetricsFactoryTest {

  private static final String ACTIVATED = "camunda.client.worker.job.activated";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerJobWorkerMetricsFactory factory =
      new MicrometerJobWorkerMetricsFactory(registry);

  @Test
  void shouldTagMetricsWithPhysicalTenantId() {
    // when
    factory
        .createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type"), "tenant-a")
        .jobActivated(3);

    // then
    assertThat(
            registry
                .find(ACTIVATED)
                .tag("type", "job-type")
                .tag("physicalTenantId", "tenant-a")
                .counter()
                .count())
        .isEqualTo(3);
  }

  @Test
  void shouldKeepPhysicalTenantsApart() {
    // when
    factory
        .createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type"), "tenant-a")
        .jobActivated(3);
    factory
        .createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type"), "tenant-b")
        .jobActivated(5);

    // then
    assertThat(registry.find(ACTIVATED).tag("physicalTenantId", "tenant-a").counter().count())
        .isEqualTo(3);
    assertThat(registry.find(ACTIVATED).tag("physicalTenantId", "tenant-b").counter().count())
        .isEqualTo(5);
  }

  @Test
  void shouldAlwaysRegisterTheSameTagKeys() {
    // when
    factory.createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type")).jobActivated(2);
    factory
        .createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type"), "tenant-a")
        .jobActivated(3);

    // then - registries such as Prometheus require one tag-key set per meter name
    assertThat(registry.find(ACTIVATED).counters())
        .hasSize(2)
        .allSatisfy(
            counter ->
                assertThat(counter.getId().getTags())
                    .extracting(tag -> tag.getKey())
                    .containsExactlyInAnyOrder("type", "physicalTenantId"));
  }

  @Test
  void shouldTagWithTheDefaultClientNameWhenNoPhysicalTenantIdIsGiven() {
    // when
    factory.createJobWorkerMetrics(new JobWorkerMetricsFactoryContext("job-type")).jobActivated(2);

    // then
    assertThat(
            registry
                .find(ACTIVATED)
                .tag("physicalTenantId", CamundaClientCreatedEvent.DEFAULT_CLIENT_NAME)
                .counter()
                .count())
        .isEqualTo(2);
  }

  @Test
  void shouldDelegateTenantAwareCreationToExistingFactoryImplementations() {
    // given
    final AtomicReference<JobWorkerMetricsFactoryContext> receivedContext = new AtomicReference<>();
    final JobWorkerMetricsFactory legacyFactory =
        context -> {
          receivedContext.set(context);
          return JobWorkerMetrics.noop();
        };
    final JobWorkerMetricsFactoryContext context = new JobWorkerMetricsFactoryContext("job-type");

    // when
    legacyFactory.createJobWorkerMetrics(context, "tenant-a");

    // then
    assertThat(receivedContext.get()).isSameAs(context);
  }
}
