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
  void shouldSearchDecisionRequirementsByName() {
    // when
    client.newDecisionRequirementsQuery().filter(f -> f.name("name")).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getName()).isEqualTo("name");
  }

  @Test
  void shouldSearchDecisionRequirementsBynameAndVersion() {
    // when
    client.newDecisionRequirementsQuery().filter(f -> f.name("name").version(1)).send().join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getFilter().getName()).isEqualTo("name");
    assertThat(request.getFilter().getVersion()).isEqualTo(1);
  }

  // Consolidated sort test
  @Test
  void shouldSortDecisionRequirements() {
    // when
    client
        .newDecisionRequirementsQuery()
        .sort(
            s ->
                s.decisionRequirementsKey()
                    .desc()
                    .version()
                    .asc()
                    .name()
                    .asc()
                    .decisionRequirementsId()
                    .asc()
                    .tenantId()
                    .desc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQueryRequest.class);
    assertThat(request.getSort()).hasSize(5);

    assertThat(request.getSort().get(0).getField()).isEqualTo("decisionRequirementsKey");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("desc");

    assertThat(request.getSort().get(1).getField()).isEqualTo("version");
    assertThat(request.getSort().get(1).getOrder()).isEqualTo("asc");

    assertThat(request.getSort().get(2).getField()).isEqualTo("name");
    assertThat(request.getSort().get(2).getOrder()).isEqualTo("asc");

    assertThat(request.getSort().get(3).getField()).isEqualTo("decisionRequirementsId");
    assertThat(request.getSort().get(3).getOrder()).isEqualTo("asc");

    assertThat(request.getSort().get(4).getField()).isEqualTo("tenantId");
    assertThat(request.getSort().get(4).getOrder()).isEqualTo("desc");
  }
}
