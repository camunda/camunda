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
import static org.awaitility.Awaitility.await;

import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies that the starter measures how long it takes for a created process instance to become
 * queryable via the search API. ProcessInstanceStartMeter polls every 500ms while the starter is
 * running; once operate indexes a record, it lands on the data-availability timer.
 */
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=120",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=true",
      "load-tester.monitor-data-availability-interval=500ms",
    })
@ActiveProfiles({"starter", "it"})
class DataAvailabilityIT {

  static final CamundaContainer CAMUNDA = CamundaContainerProvider.getCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldMeasureDataAvailabilityLatency() {
    // Long timeout: operate's first ES import on a cold testcontainer can take a few minutes.
    await()
        .atMost(Duration.ofMinutes(5))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var timer =
                  meterRegistry
                      .find(StarterLatencyMetricsDoc.DATA_AVAILABILITY_LATENCY.getName())
                      .timer();
              assertThat(timer)
                  .describedAs("Data availability latency timer should be registered")
                  .isNotNull();
              assertThat(timer.count())
                  .describedAs("At least one data availability measurement should be recorded")
                  .isGreaterThan(0);
            });
  }
}
