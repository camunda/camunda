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
package io.camunda.zeebe.exporter.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ExporterTestConfigurationTest {
  @Test
  void shouldInstantiateProvidedConfiguration() {
    // given
    final var instance = new Object();
    final var config = new ExporterTestConfiguration<>("exporter", instance);

    // when
    final var instantiated = config.instantiate(Object.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }

  @Test
  void shouldCastInstantiatedConfig() {
    // given
    final var instance = Integer.valueOf(3);
    final ExporterTestConfiguration<Object> config =
        new ExporterTestConfiguration<>("exporter", instance);

    // when
    final var instantiated = config.instantiate(Integer.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }

  @Test
  void shouldSupplyConfigurationOnInstantiate() {
    // given
    final var instance = new Object();
    final var config = new ExporterTestConfiguration<>("exporter", empty -> instance);

    // when
    final var instantiated = config.instantiate(Object.class);

    // then
    assertThat(instantiated).isSameAs(instance);
  }
}
