/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder.Names;
import io.camunda.client.metrics.JobHandlerMetrics;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerMetricsIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerMetricsIT {

  private static final String PROCESS_ID = "compatibilityMetricsProcess";
  private static final String JOB_TYPE = "compatibility-metrics-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-metrics.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void shouldRecordJobWorkerMetrics() {
    // given
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(BPMN_RESOURCE).send().join();

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance).isCompleted();

    final Counter activatedCounter =
        meterRegistry.find(Names.JOB_ACTIVATED.asString()).tag("type", JOB_TYPE).counter();
    final Counter handledCounter =
        meterRegistry.find(Names.JOB_HANDLED.asString()).tag("type", JOB_TYPE).counter();

    assertThat(activatedCounter).isNotNull();
    assertThat(handledCounter).isNotNull();
    assertThat(activatedCounter.count()).isGreaterThanOrEqualTo(1.0);
    assertThat(handledCounter.count()).isGreaterThanOrEqualTo(1.0);

    // Spring Boot Starter metrics: camunda.job.invocations (counter with action tags)
    final Counter invocationActivated =
        meterRegistry
            .find(JobHandlerMetrics.Name.INVOCATION.asString())
            .tag(JobHandlerMetrics.Tag.TYPE.asString(), JOB_TYPE)
            .tag(
                JobHandlerMetrics.Tag.ACTION.asString(),
                JobHandlerMetrics.Action.ACTIVATED.asString())
            .counter();
    final Counter invocationCompleted =
        meterRegistry
            .find(JobHandlerMetrics.Name.INVOCATION.asString())
            .tag(JobHandlerMetrics.Tag.TYPE.asString(), JOB_TYPE)
            .tag(
                JobHandlerMetrics.Tag.ACTION.asString(),
                JobHandlerMetrics.Action.COMPLETED.asString())
            .counter();

    assertThat(invocationActivated).isNotNull();
    assertThat(invocationCompleted).isNotNull();
    assertThat(invocationActivated.count()).isGreaterThanOrEqualTo(1.0);
    assertThat(invocationCompleted.count()).isGreaterThanOrEqualTo(1.0);

    // Spring Boot Starter metrics: camunda.job.execution-time (timer)
    final Timer executionTimer =
        meterRegistry
            .find(JobHandlerMetrics.Name.EXECUTION_TIME.asString())
            .tag(JobHandlerMetrics.Tag.TYPE.asString(), JOB_TYPE)
            .timer();

    assertThat(executionTimer).isNotNull();
    assertThat(executionTimer.count()).isGreaterThanOrEqualTo(1);
  }

  @Component
  public static class MetricsWorker {

    @JobWorker(type = JOB_TYPE)
    public void handleJob() {
      // do nothing, just complete the job to trigger metrics recording
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({MetricsWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
