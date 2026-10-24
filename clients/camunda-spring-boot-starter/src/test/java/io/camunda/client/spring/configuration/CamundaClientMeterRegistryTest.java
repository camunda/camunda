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

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.metrics.CamundaClientMetrics;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobWorkerMetrics;
import io.camunda.client.api.worker.metrics.MicrometerJobWorkerMetricsBuilder.Names;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The starter passes the application's meter registry to the clients it creates. Shared with the
 * Spring Boot 3 starter, which does not ship the HTTP client metrics; see {@code
 * CamundaClientHttpMetricsTest} for those.
 */
class CamundaClientMeterRegistryTest {

  @RegisterExtension
  static final WireMockExtension WM =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private static final String JOB_TYPE = "metered-job-type";
  private static final String MANUAL_JOB_TYPE = "manual-job-type";

  @BeforeEach
  void stubJobActivation() {
    WM.stubFor(post(urlPathMatching(".*/jobs/activation")).willReturn(okJson("{\"jobs\":[]}")));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
        .withUserConfiguration(WorkerConfiguration.class)
        .withBean(SimpleMeterRegistry.class)
        .withPropertyValues(
            "camunda.client.rest-address=http://localhost:" + WM.getPort(),
            "camunda.client.prefer-rest-over-grpc=true",
            "camunda.client.worker.defaults.stream-enabled=false");
  }

  @Test
  void shouldPassApplicationMeterRegistryToClient() {
    contextRunner()
        .run(
            context -> {
              // when
              final MeterRegistry registry = context.getBean(MeterRegistry.class);
              try (final io.camunda.client.api.worker.JobWorker ignored =
                  openManualWorker(context.getBean(CamundaClient.class))) {

                // then
                assertThat(manualWorkerCounter(registry)).isNotNull();
              }
            });
  }

  @Test
  void shouldLetApplicationCustomizerReplaceMeterRegistry() {
    contextRunner()
        .withUserConfiguration(CustomMeterRegistryConfiguration.class)
        .run(
            context -> {
              // when
              try (final io.camunda.client.api.worker.JobWorker ignored =
                  openManualWorker(context.getBean(CamundaClient.class))) {

                // then
                final MeterRegistry customRegistry =
                    context.getBean(CustomMeterRegistryConfiguration.class).registry;
                assertThat(manualWorkerCounter(customRegistry)).isNotNull();
                assertThat(manualWorkerCounter(context.getBean(MeterRegistry.class))).isNull();
              }
            });
  }

  @Test
  void shouldRegisterJobWorkerMetricsOfAnnotatedWorkerOnce() {
    contextRunner()
        .run(
            context -> {
              // when
              context.getBean(CamundaClient.class);

              // then — only the starter's worker metrics, not also the client's fallback ones
              final MeterRegistry registry = context.getBean(MeterRegistry.class);
              assertThat(jobActivatedMeterTags(registry))
                  .containsExactly(Tags.of("type", JOB_TYPE, "physicalTenantId", "default"));
            });
  }

  @Test
  void shouldNotRegisterFallbackJobWorkerMetricsNextToCustomOnes() {
    contextRunner()
        .withUserConfiguration(CustomJobWorkerMetricsConfiguration.class)
        .run(
            context -> {
              // when
              context.getBean(CamundaClient.class);

              // then
              final MeterRegistry registry = context.getBean(MeterRegistry.class);
              assertThat(jobActivatedMeterTags(registry))
                  .containsExactly(Tags.of("type", JOB_TYPE, "custom", "true"));
            });
  }

  /** A worker the application opens itself, without the starter's job worker metrics. */
  private static io.camunda.client.api.worker.JobWorker openManualWorker(
      final CamundaClient client) {
    return client.newWorker().jobType(MANUAL_JOB_TYPE).handler((c, j) -> {}).open();
  }

  private static Counter manualWorkerCounter(final MeterRegistry registry) {
    return registry.find(Names.JOB_ACTIVATED.asString()).tag("type", MANUAL_JOB_TYPE).counter();
  }

  private static List<Tags> jobActivatedMeterTags(final MeterRegistry registry) {
    return registry.find(Names.JOB_ACTIVATED.asString()).tag("type", JOB_TYPE).meters().stream()
        .map(Meter::getId)
        .map(id -> Tags.of(id.getTags().toArray(new Tag[0])))
        .collect(Collectors.toList());
  }

  @Configuration
  static class WorkerConfiguration {
    @Bean
    public MeteredWorker meteredWorker() {
      return new MeteredWorker();
    }
  }

  @Configuration
  static class CustomMeterRegistryConfiguration {
    // not a bean, so that the application's MeterRegistry bean stays the only one
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Bean
    public CamundaClientBuilderCustomizer customMeterRegistryCustomizer() {
      return builder ->
          builder.metrics(CamundaClientMetrics.micrometer().withMeterRegistry(registry).build());
    }
  }

  @Configuration
  static class CustomJobWorkerMetricsConfiguration {
    @Bean
    public JobWorkerMetricsFactory customJobWorkerMetricsFactory(final MeterRegistry registry) {
      return context ->
          JobWorkerMetrics.micrometer()
              .withMeterRegistry(registry)
              .withTags(Tags.of("type", context.type(), "custom", "true"))
              .build();
    }
  }

  static class MeteredWorker {
    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handle(final ActivatedJob job) {
      // no-op: only the registration of the worker metrics matters
    }
  }
}
