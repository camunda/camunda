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
package io.camunda.client.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.search.response.VariableImpl;
import io.camunda.client.protocol.rest.VariableSearchResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class VariableValueAsTypeTest {

  private final JsonMapper jsonMapper = new CamundaObjectMapper();

  @Test
  void shouldDeserializeValueAsType() {
    // given
    final VariableSearchResult searchResult =
        new VariableSearchResult()
            .name("myVar")
            .value("{\"key\":\"value\"}")
            .isTruncated(false)
            .variableKey("1")
            .scopeKey("2")
            .processInstanceKey("3")
            .tenantId("default");
    final VariableImpl variable = new VariableImpl(searchResult, jsonMapper);

    // when
    @SuppressWarnings("unchecked")
    final Map<String, String> result = variable.getValueAsType(Map.class);

    // then
    assertThat(result).containsEntry("key", "value");
  }

  @Test
  void shouldThrowExceptionForTruncatedValue() {
    // given
    final VariableSearchResult searchResult =
        new VariableSearchResult()
            .name("myVar")
            .value("{\"key\":\"val...")
            .isTruncated(true)
            .variableKey("1")
            .scopeKey("2")
            .processInstanceKey("3")
            .tenantId("default");
    final VariableImpl variable = new VariableImpl(searchResult, jsonMapper);

    // when / then
    assertThatThrownBy(() -> variable.getValueAsType(Map.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("truncated");
  }
}
