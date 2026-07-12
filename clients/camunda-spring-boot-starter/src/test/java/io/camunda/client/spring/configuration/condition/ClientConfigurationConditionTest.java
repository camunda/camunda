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
package io.camunda.client.spring.configuration.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Verifies that the single- and multi-client conditions are exact inverses driven by the presence
 * of any {@code camunda.clients.<name>.*} property, so the two auto-configurations stay mutually
 * exclusive.
 */
class ClientConfigurationConditionTest {

  private final OnSingleClientConfigurationCondition singleClient =
      new OnSingleClientConfigurationCondition();
  private final OnMultiClientConfigurationCondition multiClient =
      new OnMultiClientConfigurationCondition();
  private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

  private static ConditionContext contextWith(final Map<String, Object> properties) {
    final StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(new org.springframework.core.env.MapPropertySource("test", properties));
    final ConditionContext context = mock(ConditionContext.class);
    when(context.getEnvironment()).thenReturn(environment);
    return context;
  }

  @Test
  void shouldSelectSingleClientWhenNoClientsConfigured() {
    // given no camunda.clients.* properties
    final ConditionContext context = contextWith(Map.of("camunda.client.rest-address", "http://x"));

    // then single-client matches, multi-client does not
    assertThat(singleClient.getMatchOutcome(context, metadata).isMatch()).isTrue();
    assertThat(multiClient.getMatchOutcome(context, metadata).isMatch()).isFalse();
  }

  @Test
  void shouldSelectMultiClientWhenClientsConfigured() {
    // given a camunda.clients.<name>.* entry
    final ConditionContext context =
        contextWith(Map.of("camunda.clients.finance.physical-tenant-id", "finance"));

    // then multi-client matches, single-client does not
    assertThat(multiClient.getMatchOutcome(context, metadata).isMatch()).isTrue();
    assertThat(singleClient.getMatchOutcome(context, metadata).isMatch()).isFalse();
  }
}
