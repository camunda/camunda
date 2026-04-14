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
 * Integration test for the Starter component. Verifies that the starter can connect to a Camunda
 * cluster, deploy a BPMN process, and create process instances.
 *
 * <p>The Starter runs as a {@link org.springframework.boot.CommandLineRunner} and blocks the Spring
 * Boot startup thread until the configured {@code duration-limit} expires. By the time this test
 * method executes, the starter has already finished creating process instances and the recorded
 * metrics are available in the registry.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.starter.rate=1",
      "load-tester.starter.duration-limit=10",
      "load-tester.starter.threads=1",
      "load-tester.starter.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
    })
@ActiveProfiles({"starter", "it"})
class StarterIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private MeterRegistry meterRegistry;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldCreateProcessInstances() {
    // given - starter has run for duration-limit seconds before this test method executes

    // then - verify that process instances were created by checking the response latency metric
    final var timer =
        meterRegistry.find(StarterLatencyMetricsDoc.RESPONSE_LATENCY.getName()).timer();
    assertThat(timer).describedAs("Response latency timer should be registered").isNotNull();
    assertThat(timer.count())
        .describedAs("Starter should have created at least one process instance")
        .isGreaterThan(0);
  }
}
