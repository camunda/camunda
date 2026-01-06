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

import io.camunda.client.protocol.rest.BasicStringFilterProperty;
import io.camunda.client.protocol.rest.VariableFilter;
import io.camunda.client.protocol.rest.VariableSearchQuery;
import io.camunda.client.util.ClientRestTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class SearchVariableTest extends ClientRestTest {

  @Test
  void shouldSearchVariables() {
    // when
    client.newVariableSearchRequest().send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchVariablesByValue() {
    // when
    client.newVariableSearchRequest().filter(f -> f.value("demo")).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getValue().get$Eq()).isEqualTo("demo");
  }

  @Test
  void shouldSearchVariablesByName() {
    // when
    client.newVariableSearchRequest().filter(f -> f.name("demo")).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getName().get$Eq()).isEqualTo("demo");
  }

  @Test
  void shouldSearchVariablesByNameStringFilter() {
    // when
    client.newVariableSearchRequest().filter(f -> f.name(b -> b.in("this", "that"))).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getName().get$In()).isEqualTo(Arrays.asList("this", "that"));
  }

  @Test
  void shouldSearchVariablesByScopeKey() {
    // when
    client.newVariableSearchRequest().filter(f -> f.scopeKey(1L)).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getScopeKey().get$Eq()).isEqualTo("1");
  }

  @Test
  void shouldSearchVariablesByProcessInstanceKey() {
    // when
    client.newVariableSearchRequest().filter(f -> f.processInstanceKey(1L)).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("1");
  }

  @Test
  void shouldSearchVariablesByProcessInstanceKeyLongFilter() {
    // when
    client
        .newVariableSearchRequest()
        .sort(s -> s.processInstanceKey().asc().variableKey())
        .filter(f -> f.processInstanceKey(b -> b.in(1L, 10L)))
        .send()
        .join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    final VariableFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final BasicStringFilterProperty processInstanceKey = filter.getProcessInstanceKey();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$In()).isEqualTo(Arrays.asList("1", "10"));
  }

  @Test
  void shouldSearchVariablesByTenantId() {
    // when
    client.newVariableSearchRequest().filter(f -> f.tenantId("tenant1")).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getTenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldSearchVariablesByIsTruncated() {
    // when
    client.newVariableSearchRequest().filter(f -> f.isTruncated(true)).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getIsTruncated()).isEqualTo(true);
  }

  @Test
  void shouldSearchVariablesByVariableKey() {
    // when
    client.newVariableSearchRequest().filter(f -> f.variableKey(1L)).send().join();

    // then
    final VariableSearchQuery request = gatewayService.getLastRequest(VariableSearchQuery.class);
    assertThat(request.getFilter().getVariableKey().get$Eq()).isEqualTo("1");
  }
}
