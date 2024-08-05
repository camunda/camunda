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
package io.camunda.zeebe.client.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class SearchDecisionRequirementsTest extends ClientRestTest {

  @Test
  void shouldSearchDecisionRequirements() {
    // when
    client.newDecisionRequirementsQuery().send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchDecisionRequirementsByVersion() {
    // when
    client.newDecisionRequirementsQuery().filter(f -> f.version(1)).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getVersion()).isEqualTo(1);
  }

  @Test
  void shouldSearchDecisionRequirementsByKey() {
    // when
    client.newDecisionRequirementsQuery().filter(f -> f.decisionRequirementsKey(0L)).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getDecisionRequirementsKey()).isEqualTo(0L);
  }

  @Test
  void shouldSearchDecisionRequirementsByResourceName() {
    // when
    client
        .newDecisionRequirementsQuery()
        .filter(f -> f.dmnDecisionRequirementsName("resourceName"))
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getDmnDecisionRequirementsName()).isEqualTo("resourceName");
  }

  @Test
  void shouldSearchDecisionRequirementsByResourceNameAndVersion() {
    // when
    client
        .newDecisionRequirementsQuery()
        .filter(f -> f.dmnDecisionRequirementsName("resourceName").version(1))
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getDmnDecisionRequirementsName()).isEqualTo("resourceName");
    assertThat(request.getFilter().getVersion()).isEqualTo(1);
  }

  // Test sorting
  @Test
  void shouldSortDecisionRequirementsByKey() {
    // when
    client
        .newDecisionRequirementsQuery()
        .sort(s -> s.decisionRequirementsKey().desc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("key");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("desc");
  }

  @Test
  void shouldSortDecisionRequirementsByVersion() {
    // when
    client.newDecisionRequirementsQuery().sort(s -> s.version().asc()).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("version");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("asc");
  }

  @Test
  void shouldSortDecisionRequirementsByName() {
    // when
    client
        .newDecisionRequirementsQuery()
        .sort(s -> s.dmnDecisionRequirementsName().asc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("name");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("asc");
  }

  @Test
  void shouldSortDecisionRequirementsByResourceNameAndVersion() {
    // when
    client
        .newDecisionRequirementsQuery()
        .sort(s -> s.dmnDecisionRequirementsName().asc().version().desc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("name");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(1).getField()).isEqualTo("version");
    assertThat(request.getSort().get(1).getOrder()).isEqualTo("desc");
  }

  @Test
  void shouldSortDecisionRequirementsByDecisionRequirementsId() {
    // when
    client
        .newDecisionRequirementsQuery()
        .sort(s -> s.dmnDecisionRequirementsId().asc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("decisionRequirementsId");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("asc");
  }

  @Test
  void shouldSortDecisionRequirementsByTenantId() {
    // when
    client.newDecisionRequirementsQuery().sort(s -> s.tenantId().desc()).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort().get(0).getField()).isEqualTo("tenantId");
  }
}
