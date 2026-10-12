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
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class CamundaClientBuilderMeterRegistryTest {

  @Test
  void shouldHaveNoMeterRegistryByDefault() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // then
    assertThat(builder.getMeterRegistry()).isNull();
  }

  @Test
  void shouldExposeConfiguredMeterRegistry() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();

    // when
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withMeterRegistry(registry);

    // then
    assertThat(builder.getMeterRegistry()).isSameAs(registry);
  }

  @Test
  void shouldCopyMeterRegistryFromConfiguration() {
    // given
    final MeterRegistry registry = new SimpleMeterRegistry();
    final CamundaClientBuilderImpl source = new CamundaClientBuilderImpl();
    source.withMeterRegistry(registry);

    // when
    final CamundaClientBuilderImpl copy = new CamundaClientBuilderImpl();
    copy.withConfiguration(source);

    // then
    assertThat(copy.getMeterRegistry()).isSameAs(registry);
  }
}
