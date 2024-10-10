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
package io.camunda.zeebe.client.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.VariableSearchQueryRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class SearchVariableTest extends ClientRestTest {

  @Test
  void shouldSearchVariables() {
    // when
    client.newVariableQuery().send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchVariablesByValue() {
    // when
    client.newVariableQuery().filter(f -> f.value("demo")).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getValue()).isEqualTo("demo");
  }

  @Test
  void shouldSearchVariablesByName() {
    // when
    client.newVariableQuery().filter(f -> f.name("demo")).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getName()).isEqualTo("demo");
  }

  @Test
  void shouldSearchVariablesByScopeKey() {
    // when
    client.newVariableQuery().filter(f -> f.scopeKey(1L)).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getScopeKey()).isEqualTo(1);
  }

  @Test
  void shouldSearchVariablesByProcessInstanceKey() {
    // when
    client.newVariableQuery().filter(f -> f.processInstanceKey(1L)).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getProcessInstanceKey()).isEqualTo(1);
  }

  @Test
  void shouldSearchVariablesByTenantId() {
    // when
    client.newVariableQuery().filter(f -> f.tenantId("tenant1")).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getTenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldSearchVariablesByIsTruncated() {
    // when
    client.newVariableQuery().filter(f -> f.isTruncated(true)).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getIsTruncated()).isEqualTo(true);
  }

  @Test
  void shouldSearchVariablesByVariableKey() {
    // when
    client.newVariableQuery().filter(f -> f.variableKey(1L)).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter().getVariableKey()).isEqualTo(1);
  }
}
