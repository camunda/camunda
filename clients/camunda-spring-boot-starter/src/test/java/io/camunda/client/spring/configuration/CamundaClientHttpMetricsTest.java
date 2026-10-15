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
 * The starter ships the httpclient5 observation module, so its clients expose HTTP client metrics
 * to the application's meter registry. Not shared with the Spring Boot 3 starter, which does not
 * ship the module.
 */
class CamundaClientHttpMetricsTest {

  @Test
  void shouldExposeHttpClientMetricsToApplicationMeterRegistry() {
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
              context.getBean(CamundaClient.class);

              // then
              final MeterRegistry registry = context.getBean(MeterRegistry.class);
              assertThat(registry.find("http.client.pool.leased").tag("client", "camunda").gauge())
                  .isNotNull();
              assertThat(registry.find("http.client.pool.pending").tag("client", "camunda").gauge())
                  .isNotNull();
            });
  }
}
