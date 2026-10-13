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
package io.camunda.client.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

final class MicrometerClientMetricsBinderTest {

  private static ObjectProvider<MeterRegistry> providerOf(final MeterRegistry registry) {
    final StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    if (registry != null) {
      beanFactory.addBean("meterRegistry", registry);
    }
    return beanFactory.getBeanProvider(MeterRegistry.class);
  }

  private static CamundaClientBuilderImpl newBuilder() {
    return (CamundaClientBuilderImpl) CamundaClient.newClientBuilder();
  }

  @Test
  void shouldSetMeterRegistryOnBuilder() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();
    final CamundaClientBuilderImpl builder = newBuilder();

    // when
    final CamundaClientBuilder result =
        new MicrometerClientMetricsBinder(providerOf(registry)).bind(builder);

    // then
    assertThat(result).isSameAs(builder);
    assertThat(builder.getMeterRegistry()).isSameAs(registry);
  }

  @Test
  void shouldLeaveBuilderUntouchedWithoutRegistryBean() {
    // given
    final CamundaClientBuilderImpl builder = newBuilder();

    // when
    new MicrometerClientMetricsBinder(providerOf(null)).bind(builder);

    // then
    assertThat(builder.getMeterRegistry()).isNull();
  }

  @Test
  void shouldLeaveBuilderUntouchedWithNoopBinder() {
    // given
    final CamundaClientBuilderImpl builder = newBuilder();

    // when
    new NoopClientMetricsBinder().bind(builder);

    // then
    assertThat(builder.getMeterRegistry()).isNull();
  }
}
