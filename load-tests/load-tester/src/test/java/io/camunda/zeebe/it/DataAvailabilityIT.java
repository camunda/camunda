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
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the data availability monitoring. Verifies that the starter can measure how
 * long it takes for a created process instance to become queryable via the search API.
 *
 * <p>This test relies on the {@link CamundaContainer}'s built-in H2 database and RDBMS exporter,
 * which flush immediately (PT0S), making process instances queryable almost instantly.
 *
 * <p>The starter runs for {@code duration-limit} seconds (blocking Spring Boot startup). During
 * that time, it creates instances and the {@link
 * io.camunda.zeebe.metrics.ProcessInstanceStartMeter} periodically checks if they are queryable. By
 * the time this test method executes, the data availability latency metric should have recordings.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=30",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=true",
      "load-tester.monitor-data-availability-interval=500ms",
    })
@ActiveProfiles({"starter", "it"})
class DataAvailabilityIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldMeasureDataAvailabilityLatency() {
    // given - starter has run for duration-limit seconds with data availability monitoring

    // then - verify that data availability latency was recorded
    final var timer =
        meterRegistry.find(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName()).timer();
    assertThat(timer)
        .describedAs("Data availability latency timer should be registered")
        .isNotNull();
    assertThat(timer.count())
        .describedAs("At least one data availability measurement should be recorded")
        .isGreaterThan(0);
  }
}
