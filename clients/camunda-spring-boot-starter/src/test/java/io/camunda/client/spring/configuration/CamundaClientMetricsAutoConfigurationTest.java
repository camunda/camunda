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
import io.camunda.client.metrics.ClientMetricsBinder;
import io.camunda.client.metrics.MicrometerClientMetricsBinder;
import io.camunda.client.metrics.NoopClientMetricsBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CamundaClientMetricsAutoConfigurationTest {

  private static final String CLIENT_BEAN = "financeCamundaClient";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class))
          .withPropertyValues(
              "camunda.client.rest-address=http://localhost:8080",
              "camunda.clients.finance.physical-tenant-id=finance");

  @Test
  void shouldBindApplicationMeterRegistryToTheClient() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();

    // when / then
    contextRunner
        .withBean(MeterRegistry.class, () -> registry)
        .run(
            context -> {
              assertThat(context.getBean(ClientMetricsBinder.class))
                  .isInstanceOf(MicrometerClientMetricsBinder.class);
              assertThat(
                      context
                          .getBean(CLIENT_BEAN, CamundaClient.class)
                          .getConfiguration()
                          .getMeterRegistry())
                  .isSameAs(registry);
            });
  }

  @Test
  void shouldBuildClientWithoutRegistryWhenNoMeterRegistryBeanExists() {
    contextRunner.run(
        context ->
            assertThat(
                    context
                        .getBean(CLIENT_BEAN, CamundaClient.class)
                        .getConfiguration()
                        .getMeterRegistry())
                .isNull());
  }

  @Test
  void shouldUseNoopBinderWithoutObservationModule() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.apache.hc.client5.http.observation"))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              assertThat(context.getBean(ClientMetricsBinder.class))
                  .isInstanceOf(NoopClientMetricsBinder.class);
              assertThat(
                      context
                          .getBean(CLIENT_BEAN, CamundaClient.class)
                          .getConfiguration()
                          .getMeterRegistry())
                  .isNull();
            });
  }
}
