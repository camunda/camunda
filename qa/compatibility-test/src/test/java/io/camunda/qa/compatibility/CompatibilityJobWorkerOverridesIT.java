/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerOverridesIT.TestApplication.class,
    properties = {
      "spring.main.web-application-type=none",
      "camunda.client.worker.defaults.timeout=PT45S",
      "camunda.client.worker.defaults.max-jobs-active=10",
      "camunda.client.worker.override.compatibility-override-worker.timeout=PT12S",
      "camunda.client.worker.override.compatibility-override-worker.poll-interval=PT2S",
      "camunda.client.worker.override.compatibility-override-worker.max-retries=3",
      "camunda.client.worker.override.compatibility-override-worker.tenant-ids[0]=tenant-a",
      "camunda.client.worker.override.compatibility-override-worker.tenant-ids[1]=tenant-b"
    })
@CamundaSpringProcessTest
class CompatibilityJobWorkerOverridesIT {

  private static final String JOB_TYPE = "compatibility-override-worker";

  @Autowired private JobWorkerManager jobWorkerManager;

  @Test
  void shouldApplyDefaultsAndOverridesForJobWorker() {
    final JobWorkerValue jobWorkerValue = jobWorkerManager.getJobWorker(JOB_TYPE);

    assertThat(jobWorkerValue.getTimeout())
        .isInstanceOf(JobWorkerValue.SourceAware.FromOverrideProperty.class);
    assertThat(jobWorkerValue.getTimeout().value()).isEqualTo(Duration.ofSeconds(12));

    assertThat(jobWorkerValue.getPollInterval())
        .isInstanceOf(JobWorkerValue.SourceAware.FromOverrideProperty.class);
    assertThat(jobWorkerValue.getPollInterval().value()).isEqualTo(Duration.ofSeconds(2));

    assertThat(jobWorkerValue.getMaxRetries())
        .isInstanceOf(JobWorkerValue.SourceAware.FromOverrideProperty.class);
    assertThat(jobWorkerValue.getMaxRetries().value()).isEqualTo(3);

    assertThat(jobWorkerValue.getMaxJobsActive())
        .isInstanceOf(JobWorkerValue.SourceAware.FromDefaultProperty.class);
    assertThat(jobWorkerValue.getMaxJobsActive().value()).isEqualTo(10);

    final List<JobWorkerValue.SourceAware<String>> tenantIds = jobWorkerValue.getTenantIds();
    assertThat(tenantIds).hasSize(2);
    assertThat(tenantIds)
        .allMatch(JobWorkerValue.SourceAware.FromOverrideProperty.class::isInstance);
    assertThat(tenantIds.stream().map(JobWorkerValue.SourceAware::value).toList())
        .containsExactly("tenant-a", "tenant-b");
  }

  @Component
  public static class OverrideWorker {

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob() {}
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({OverrideWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
