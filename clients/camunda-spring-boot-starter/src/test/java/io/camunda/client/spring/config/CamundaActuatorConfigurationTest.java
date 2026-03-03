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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.NoopJobWorkerMetricsFactory;
import io.camunda.client.spring.actuator.JobWorkerController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests that auto-configuration degrades gracefully when optional dependencies (actuator,
 * micrometer) are absent from the classpath. This is a regression test for Java 24+ compatibility
 * where Spring Framework's ClassFileMetadataReader eagerly resolves class literals in annotations.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/47464">#47464</a>
 */
class CamundaActuatorConfigurationTest {

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            CamundaActuatorConfiguration.class, MetricsDefaultConfiguration.class);
  }

  @Test
  void shouldSkipActuatorConfigWhenActuatorAutoconfigureAbsent() {
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JobWorkerController.class);
              assertThat(context).doesNotHaveBean("micrometerMetricsRecorder");
            });
  }

  @Test
  void shouldSkipActuatorConfigWhenMicrometerAbsent() {
    contextRunner()
        .withClassLoader(new FilteredClassLoader("io.micrometer.core.instrument.MeterRegistry"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JobWorkerController.class);
              assertThat(context).doesNotHaveBean("micrometerMetricsRecorder");
            });
  }

  @Test
  void shouldFallBackToNoopMetricsWhenActuatorAbsent() {
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(MetricsRecorder.class);
              assertThat(context.getBean(MetricsRecorder.class))
                  .isInstanceOf(DefaultNoopMetricsRecorder.class);

              assertThat(context).hasSingleBean(JobWorkerMetricsFactory.class);
              assertThat(context.getBean(JobWorkerMetricsFactory.class))
                  .isInstanceOf(NoopJobWorkerMetricsFactory.class);
            });
  }

  @Test
  void shouldFallBackToNoopMetricsWhenMicrometerAbsent() {
    contextRunner()
        .withClassLoader(new FilteredClassLoader("io.micrometer.core.instrument.MeterRegistry"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(MetricsRecorder.class);
              assertThat(context.getBean(MetricsRecorder.class))
                  .isInstanceOf(DefaultNoopMetricsRecorder.class);

              assertThat(context).hasSingleBean(JobWorkerMetricsFactory.class);
              assertThat(context.getBean(JobWorkerMetricsFactory.class))
                  .isInstanceOf(NoopJobWorkerMetricsFactory.class);
            });
  }

  @Test
  void shouldFallBackToNoopMetricsWhenBothAbsent() {
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration",
                "io.micrometer.core.instrument.MeterRegistry"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(JobWorkerController.class);

              assertThat(context).hasSingleBean(MetricsRecorder.class);
              assertThat(context.getBean(MetricsRecorder.class))
                  .isInstanceOf(DefaultNoopMetricsRecorder.class);

              assertThat(context).hasSingleBean(JobWorkerMetricsFactory.class);
              assertThat(context.getBean(JobWorkerMetricsFactory.class))
                  .isInstanceOf(NoopJobWorkerMetricsFactory.class);
            });
  }
}
