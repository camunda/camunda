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

import io.camunda.client.protocol.rest.DecisionDefinitionSearchQuery;
import io.camunda.client.protocol.rest.DecisionDefinitionSearchQuerySortRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public final class SearchDecisionDefinitionTest extends ClientRestTest {

  @Test
  void shouldSearchDecisionDefinition() {
    // when
    client.newDecisionDefinitionSearchRequest().send().join();

    // then
    final DecisionDefinitionSearchQuery request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchDecisionDefinitionWithFullFilters() {
    // when
    client
        .newDecisionDefinitionSearchRequest()
        .filter(
            f ->
                f.decisionDefinitionKey(1L)
                    .decisionDefinitionId("ddi")
                    .name("ddm")
                    .decisionRequirementsKey(2L)
                    .decisionRequirementsId("ddri")
                    .version(3)
                    .tenantId("t"))
        .send()
        .join();

    // then
    final DecisionDefinitionSearchQuery request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQuery.class);
    assertThat(request.getFilter().getDecisionDefinitionKey()).isEqualTo("1");
    assertThat(request.getFilter().getDecisionDefinitionId()).isEqualTo("ddi");
    assertThat(request.getFilter().getName()).isEqualTo("ddm");
    assertThat(request.getFilter().getDecisionRequirementsKey()).isEqualTo("2");
    assertThat(request.getFilter().getDecisionRequirementsId()).isEqualTo("ddri");
    assertThat(request.getFilter().getVersion()).isEqualTo(3);
    assertThat(request.getFilter().getTenantId()).isEqualTo("t");
  }

  @Test
  void shouldSearchDecisionDefinitionWithFullSorting() {
    // when
    client
        .newDecisionDefinitionSearchRequest()
        .sort(
            s ->
                s.decisionDefinitionKey()
                    .asc()
                    .decisionDefinitionId()
                    .asc()
                    .name()
                    .desc()
                    .decisionRequirementsKey()
                    .asc()
                    .decisionRequirementsId()
                    .asc()
                    .version()
                    .asc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final DecisionDefinitionSearchQuery request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQuery.class);
    assertThat(request.getSort().size()).isEqualTo(7);
    assertThat(request.getSort().get(0).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_KEY);
    assertThat(request.getSort().get(0).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(1).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_ID);
    assertThat(request.getSort().get(1).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(2).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.NAME);
    assertThat(request.getSort().get(2).getOrder()).isEqualTo(SortOrderEnum.DESC);
    assertThat(request.getSort().get(3).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.DECISION_REQUIREMENTS_KEY);
    assertThat(request.getSort().get(3).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(4).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.DECISION_REQUIREMENTS_ID);
    assertThat(request.getSort().get(4).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(5).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.VERSION);
    assertThat(request.getSort().get(5).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(6).getField())
        .isEqualTo(DecisionDefinitionSearchQuerySortRequest.FieldEnum.TENANT_ID);
    assertThat(request.getSort().get(6).getOrder()).isEqualTo(SortOrderEnum.ASC);
  }
}
