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
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the starter loop self-terminates when {@code duration-limit} elapses, leaving {@code
 * starter.run.finished} at 1. Spring startup blocks on the {@code CommandLineRunner}, so the test
 * body runs after the starter exits — no Awaitility. Excludes the {@code worker} profile to avoid
 * the known {@code @SpringBootTest} hang.
 */
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=2",
      "load-tester.starter.duration-limit=3",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"starter", "it"})
@Disabled
class StarterDurationLimitIT {

  static final CamundaContainer CAMUNDA = CamundaContainerProvider.getCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldStopAfterDurationLimit() {
    // given — starter has already run (CommandLineRunner blocks context startup for
    //         duration-limit=3s) and exited via the timed continuation condition.

    // then — the run-finished gauge should be 1, proving the loop exited cleanly via the
    //        deadline (not via cancellation or an error path).
    final var runFinishedGauge =
        meterRegistry.find(StarterMetricsDoc.RUN_FINISHED.getName()).gauge();
    assertThat(runFinishedGauge)
        .describedAs("starter.run.finished gauge should be registered")
        .isNotNull();
    assertThat(runFinishedGauge.value())
        .describedAs("gauge should be 1 after the duration-limit elapsed")
        .isEqualTo(1.0);

    // and — at least one start request was submitted, ruling out an immediate stop.
    final var counter =
        meterRegistry.find(StarterMetricsDoc.PROCESS_INSTANCES_STARTED.getName()).counter();
    assertThat(counter)
        .describedAs("starter.process.instances.started counter should be registered")
        .isNotNull();
    assertThat(counter.count())
        .describedAs("counter should reflect that the loop ran at least once before stopping")
        .isGreaterThan(0.0);
  }
}
