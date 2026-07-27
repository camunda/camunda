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
package io.camunda.client.spring.bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultCamundaClientRegistryTest {

  private final CamundaClient finance = mock(CamundaClient.class);
  private final CamundaClient risk = mock(CamundaClient.class);
  // resolver from bean name -> client, matching the <name>CamundaClient convention
  private final Map<String, CamundaClient> beansByName =
      Map.of("financeCamundaClient", finance, "riskCamundaClient", risk);

  private Map<String, String> beanNames() {
    final Map<String, String> beanNames = new LinkedHashMap<>();
    beanNames.put("finance", "financeCamundaClient");
    beanNames.put("risk", "riskCamundaClient");
    return beanNames;
  }

  private DefaultCamundaClientRegistry registry(final String primaryClientName) {
    return new DefaultCamundaClientRegistry(beanNames(), beansByName::get, primaryClientName);
  }

  @Test
  void shouldReturnClientByName() {
    // given
    final CamundaClientRegistry registry = registry("finance");

    // when / then
    assertThat(registry.get("finance")).isSameAs(finance);
    assertThat(registry.get("risk")).isSameAs(risk);
  }

  @Test
  void shouldThrowWhenClientNameUnknown() {
    // given
    final CamundaClientRegistry registry = registry("finance");

    // when / then
    assertThatThrownBy(() -> registry.get("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown")
        .hasMessageContaining("finance")
        .hasMessageContaining("risk");
  }

  @Test
  void shouldFindClientByName() {
    // given
    final CamundaClientRegistry registry = registry("finance");

    // when / then
    assertThat(registry.find("finance")).containsSame(finance);
    assertThat(registry.find("unknown")).isEmpty();
  }

  @Test
  void shouldReturnPrimaryClient() {
    // given risk is designated primary
    final CamundaClientRegistry registry = registry("risk");

    // when / then
    assertThat(registry.getPrimary()).isSameAs(risk);
  }

  @Test
  void shouldThrowWhenNoPrimaryDesignated() {
    // given no primary
    final CamundaClientRegistry registry = registry(null);

    // when / then
    assertThatThrownBy(registry::getPrimary)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("primary");
  }

  @Test
  void shouldExposeNamesAndAllClients() {
    // given
    final CamundaClientRegistry registry = registry("finance");

    // when / then
    assertThat(registry.clientNames()).containsExactly("finance", "risk");
    assertThat(registry.all())
        .containsExactly(Map.entry("finance", finance), Map.entry("risk", risk));
  }
}
