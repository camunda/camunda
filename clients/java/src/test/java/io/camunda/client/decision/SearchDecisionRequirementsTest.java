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
package io.camunda.client.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.DecisionRequirementsSearchQuery;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQuerySortRequest;
import io.camunda.client.protocol.rest.DecisionRequirementsSearchQuerySortRequest.FieldEnum;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class SearchDecisionRequirementsTest extends ClientRestTest {

  @Test
  void shouldSearchDecisionRequirements() {
    // when
    client.newDecisionRequirementsSearchRequest().send().join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchDecisionRequirementsByVersion() {
    // when
    client.newDecisionRequirementsSearchRequest().filter(f -> f.version(1)).send().join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getFilter().getVersion()).isEqualTo(1);
  }

  @Test
  void shouldSearchDecisionRequirementsByKey() {
    // when
    client
        .newDecisionRequirementsSearchRequest()
        .filter(f -> f.decisionRequirementsKey(0L))
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getFilter().getDecisionRequirementsKey()).isEqualTo("0");
  }

  @Test
  void shouldSearchDecisionRequirementsByName() {
    // when
    client
        .newDecisionRequirementsSearchRequest()
        .filter(f -> f.decisionRequirementsName("name"))
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getFilter().getDecisionRequirementsName()).isEqualTo("name");
  }

  @Test
  void shouldSearchDecisionRequirementsBynameAndVersion() {
    // when
    client
        .newDecisionRequirementsSearchRequest()
        .filter(f -> f.decisionRequirementsName("name").version(1))
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getFilter().getDecisionRequirementsName()).isEqualTo("name");
    assertThat(request.getFilter().getVersion()).isEqualTo(1);
  }

  // Consolidated sort test
  @Test
  void shouldSortDecisionRequirements() {
    // when
    client
        .newDecisionRequirementsSearchRequest()
        .sort(
            s ->
                s.decisionRequirementsKey()
                    .desc()
                    .version()
                    .asc()
                    .decisionRequirementsName()
                    .asc()
                    .decisionRequirementsId()
                    .asc()
                    .tenantId()
                    .desc())
        .send()
        .join();

    // then
    final DecisionRequirementsSearchQuery request =
        gatewayService.getLastRequest(DecisionRequirementsSearchQuery.class);
    assertThat(request.getSort()).hasSize(5);

    assertThat(request.getSort().get(0).getField())
        .isEqualTo(DecisionRequirementsSearchQuerySortRequest.FieldEnum.DECISION_REQUIREMENTS_KEY);
    assertThat(request.getSort().get(0).getOrder()).isEqualTo(SortOrderEnum.DESC);

    assertThat(request.getSort().get(1).getField())
        .isEqualTo(DecisionRequirementsSearchQuerySortRequest.FieldEnum.VERSION);
    assertThat(request.getSort().get(1).getOrder()).isEqualTo(SortOrderEnum.ASC);

    assertThat(request.getSort().get(2).getField()).isEqualTo(FieldEnum.DECISION_REQUIREMENTS_NAME);
    assertThat(request.getSort().get(2).getOrder()).isEqualTo(SortOrderEnum.ASC);

    assertThat(request.getSort().get(3).getField())
        .isEqualTo(DecisionRequirementsSearchQuerySortRequest.FieldEnum.DECISION_REQUIREMENTS_ID);
    assertThat(request.getSort().get(3).getOrder()).isEqualTo(SortOrderEnum.ASC);

    assertThat(request.getSort().get(4).getField())
        .isEqualTo(DecisionRequirementsSearchQuerySortRequest.FieldEnum.TENANT_ID);
    assertThat(request.getSort().get(4).getOrder()).isEqualTo(SortOrderEnum.DESC);
  }
}
