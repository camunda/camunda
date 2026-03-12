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
package io.camunda.zeebe.spring.client.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests that auto-configuration degrades gracefully when optional dependencies (actuator,
 * micrometer) are absent from the classpath. This is a regression test for Java 24+ compatibility
 * where Spring Framework's ClassFileMetadataReader eagerly resolves class literals in annotations.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/47464">#47464</a>
 */
class ZeebeActuatorConfigurationTest {

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ZeebeActuatorConfiguration.class, MetricsDefaultConfiguration.class))
        .withPropertyValues("management.health.zeebe.enabled=false");
  }

  @Test
  void shouldSkipActuatorConfigWhenActuatorAutoconfigureAbsent() {
    contextRunner()
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("micrometerMetricsRecorder");
            });
  }

  @Test
  void shouldSkipActuatorConfigWhenMicrometerAbsent() {
    contextRunner()
        .withClassLoader(new FilteredClassLoader("io.micrometer.core.instrument.MeterRegistry"))
        .run(
            context -> {
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
              assertThat(context).doesNotHaveBean("micrometerMetricsRecorder");

              assertThat(context).hasSingleBean(MetricsRecorder.class);
              assertThat(context.getBean(MetricsRecorder.class))
                  .isInstanceOf(DefaultNoopMetricsRecorder.class);
            });
  }

  @Test
  void shouldFallBackToNoopMetricsWhenMeterRegistryBeanMissing() {
    // Actuator and Micrometer classes are present on the classpath, but no MeterRegistry bean
    contextRunner()
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("micrometerMetricsRecorder");
              assertThat(context).hasSingleBean(MetricsRecorder.class);
              assertThat(context.getBean(MetricsRecorder.class))
                  .isInstanceOf(DefaultNoopMetricsRecorder.class);
            });
  }
}
