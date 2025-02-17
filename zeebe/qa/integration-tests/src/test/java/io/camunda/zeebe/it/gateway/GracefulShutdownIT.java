/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class GracefulShutdownIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .withGatewayConfig(
              cfg -> cfg.withProperty("spring.lifecycle.timeout-per-shutdown-phase", "20s"))
          .build();

  @Test
  void shouldShutdownGracefully() {
    // given -- an open job stream that needs to wait for an activated job
    final var jobType = Strings.newRandomValidBpmnId();
    final var activatedJob = new AtomicReference<ActivatedJob>();
    final var model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("timer", e -> e.timerWithDuration(Duration.ofSeconds(5)))
            .serviceTask("task", task -> task.zeebeJobType(jobType))
            .endEvent()
            .done();
    try (final var client = cluster.newClientBuilder().build()) {
      client.newDeployResourceCommand().addProcessModel(model, "process.bpmn").send().join();
      client.newStreamJobsCommand().jobType(jobType).consumer(activatedJob::set).send();
      client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

      assertThat(activatedJob).hasNullValue();

      // when -- shutting down the gateways
      cluster.gateways().values().forEach(TestApplication::close);

      // then -- job is still activated because gateway is maintaining the connection
      Awaitility.await("job is activated")
          .untilAsserted(() -> assertThat(activatedJob).doesNotHaveNullValue());
    }
  }
}
