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
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end IT that activates both the starter and worker profiles. Verifies the starter records
 * response latency, which proves the starter created instances against the broker.
 */
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=120",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.worker.completion-delay=0ms",
      "load-tester.worker.payload-path=bpmn/small_payload.json",
    })
@ActiveProfiles({"starter", "worker", "it"})
@Disabled(
    "Temporariy disabled due to https://camunda.slack.com/archives/C071KP5BTHB/p1779098983781369")
class StarterWorkerIT {

  static final CamundaContainer CAMUNDA = CamundaContainerProvider.getCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldStartInstancesAndCompleteThem() {
    // Long timeout: operate's first ES import on a cold testcontainer can take a few minutes.
    await()
        .atMost(Duration.ofMinutes(5))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var responseLatency =
                  meterRegistry.find(StarterLatencyMetricsDoc.RESPONSE_LATENCY.getName()).timer();
              assertThat(responseLatency)
                  .describedAs("Starter response-latency timer should be registered")
                  .isNotNull();
              assertThat(responseLatency.count())
                  .describedAs("Starter should have created at least one process instance")
                  .isGreaterThan(0);
            });

    // and — the starter should have exposed a counter that recorded each submitted request.
    final var counter =
        meterRegistry.find(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName()).counter();
    assertThat(counter)
        .describedAs("starter.process.instances.started counter should be registered")
        .isNotNull();
    assertThat(counter.count())
        .describedAs("counter should reflect the number of submitted start requests (>0)")
        .isGreaterThan(0.0);

    // and — the run-finished gauge should have flipped to 1 once the duration limit elapsed.
    final var runFinishedGauge =
        meterRegistry.find(StarterMetricsDoc.RUN_FINISHED.getName()).gauge();
    assertThat(runFinishedGauge)
        .describedAs("starter.run.finished gauge should be registered")
        .isNotNull();
    assertThat(runFinishedGauge.value())
        .describedAs("gauge should be 1 after the starter finished its creation loop")
        .isEqualTo(1.0);
  }
}
