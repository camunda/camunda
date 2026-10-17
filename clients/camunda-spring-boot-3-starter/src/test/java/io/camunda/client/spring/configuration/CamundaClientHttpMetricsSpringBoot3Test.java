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

import io.camunda.client.CamundaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Spring Boot 3.5 manages an httpclient5 version older than the observation module needs, so this
 * starter does not ship the module: its clients are created without HTTP client metrics.
 */
class CamundaClientHttpMetricsSpringBoot3Test {

  @Test
  void shouldCreateClientWithoutHttpClientMetrics() {
    // given
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
        .withBean(SimpleMeterRegistry.class)
        .withPropertyValues(
            "camunda.client.rest-address=http://localhost:8080",
            "camunda.client.prefer-rest-over-grpc=true")
        .run(
            context -> {
              // when
              final CamundaClient client = context.getBean(CamundaClient.class);

              // then
              assertThat(client).isNotNull();
              final MeterRegistry registry = context.getBean(MeterRegistry.class);
              assertThat(registry.find("http.client.pool.leased").gauges()).isEmpty();
            });
  }
}
