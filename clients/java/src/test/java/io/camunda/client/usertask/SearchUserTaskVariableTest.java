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
package io.camunda.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.VariableSearchQuery;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public final class SearchUserTaskVariableTest extends ClientRestTest {

  @Test
  void shouldSearchVariables() {
    final long userTaskKey = 1L;

    // when
    client.newUserTaskVariableSearchRequest(userTaskKey).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchVariablesByNameWithEqOperator() {
    final long userTaskKey = 1L;
    final String variableName = "variableName";

    // when
    client
        .newUserTaskVariableSearchRequest(userTaskKey)
        .filter(f -> f.name(variableName))
        .send()
        .join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getName().get$Eq()).isEqualTo(variableName);
  }

  @Test
  void shouldSearchVariablesByNameWithLikeOperator() {
    final long userTaskKey = 1L;
    final String variableName = "variableName";

    // when
    client
        .newUserTaskVariableSearchRequest(userTaskKey)
        .filter(f -> f.name(b -> b.like(variableName)))
        .send()
        .join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getName().get$Like()).isEqualTo(variableName);
  }

  @Test
  void shouldSearchVariablesByNameWithInOperator() {
    // Given
    final long userTaskKey = 1L;
    final String variableName = "variableName";

    // When
    client
        .newUserTaskVariableSearchRequest(userTaskKey)
        .filter(f -> f.name(b -> b.in(variableName)))
        .send()
        .join();

    // Then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getName().get$In()).containsExactly(variableName);
  }
}
