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
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerAnnotationSettingsIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerAnnotationSettingsIT {

  private static final String ANNOTATION_JOB_TYPE = "compatibility-annotation-worker";
  private static final String FORCE_FETCH_JOB_TYPE = "compatibility-force-fetch-worker";

  @Autowired private JobWorkerManager jobWorkerManager;

  @Test
  void shouldApplyAnnotationJobWorkerSettings() {
    final JobWorkerValue jobWorkerValue = jobWorkerManager.getJobWorker(ANNOTATION_JOB_TYPE);

    assertThat(jobWorkerValue.getType())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getType().value()).isEqualTo(ANNOTATION_JOB_TYPE);

    assertThat(jobWorkerValue.getTimeout())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getTimeout().value()).isEqualTo(Duration.ofMinutes(2));

    assertThat(jobWorkerValue.getMaxJobsActive())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getMaxJobsActive().value()).isEqualTo(3);

    assertThat(jobWorkerValue.getRequestTimeout())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getRequestTimeout().value()).isEqualTo(Duration.ofSeconds(45));

    assertThat(jobWorkerValue.getPollInterval())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getPollInterval().value()).isEqualTo(Duration.ofSeconds(3));

    assertThat(jobWorkerValue.getStreamEnabled())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getStreamEnabled().value()).isTrue();

    assertThat(jobWorkerValue.getStreamTimeout())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getStreamTimeout().value()).isEqualTo(Duration.ofSeconds(15));

    assertThat(jobWorkerValue.getMaxRetries())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getMaxRetries().value()).isEqualTo(7);

    assertThat(jobWorkerValue.getRetryBackoff())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getRetryBackoff().value()).isEqualTo(Duration.ofSeconds(2));

    assertThat(jobWorkerValue.getEnabled())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getEnabled().value()).isTrue();

    assertThat(jobWorkerValue.getAutoComplete())
        .isInstanceOf(JobWorkerValue.SourceAware.FromAnnotation.class);
    assertThat(jobWorkerValue.getAutoComplete().value()).isFalse();

    assertThat(jobWorkerValue.getFetchVariables())
        .extracting(JobWorkerValue.SourceAware::value)
        .contains("foo", "bar");
  }

  @Test
  void shouldForceFetchAllVariablesWhenActivatedJobUsed() {
    final JobWorkerValue jobWorkerValue = jobWorkerManager.getJobWorker(FORCE_FETCH_JOB_TYPE);

    assertThat(jobWorkerValue.getForceFetchAllVariables())
        .isInstanceOf(JobWorkerValue.SourceAware.FromDefaultProperty.class);
    assertThat(jobWorkerValue.getForceFetchAllVariables().value()).isFalse();

    assertThat(jobWorkerValue.getFetchVariables())
        .extracting(JobWorkerValue.SourceAware::value)
        .contains("foo");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    AnnotationSettingsWorker.class,
    ForceFetchAllWorker.class,
    CompatibilityTestSupportConfiguration.class
  })
  static class TestApplication {}

  @Component
  public static class AnnotationSettingsWorker {

    @JobWorker(
        type = ANNOTATION_JOB_TYPE,
        timeout = 120_000,
        maxJobsActive = 3,
        requestTimeout = 45,
        pollInterval = 3_000,
        streamEnabled = {true},
        streamTimeout = 15_000,
        maxRetries = 7,
        retryBackoff = 2_000,
        enabled = {true},
        autoComplete = {false},
        fetchVariables = {"foo", "bar"})
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {}
  }

  @Component
  public static class ForceFetchAllWorker {

    @JobWorker(
        type = FORCE_FETCH_JOB_TYPE,
        fetchVariables = {"foo"})
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {}
  }
}
