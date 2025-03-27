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

import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SearchDecisionInstanceTest extends ClientRestTest {

  @Test
  void shouldSearchDecisionInstance() {
    // when
    client.newDecisionInstanceSearchRequest().send().join();

    // then
    final DecisionInstanceSearchQuery request =
        gatewayService.getLastRequest(DecisionInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchDecisionInstanceWithFullFilters() {
    // when
    client
        .newDecisionInstanceSearchRequest()
        .filter(
            f ->
                f.decisionInstanceKey(1L)
                    .state(DecisionInstanceState.FAILED)
                    .evaluationFailure("ef")
                    .decisionDefinitionType(DecisionDefinitionType.DECISION_TABLE)
                    .processDefinitionKey(2L)
                    .processInstanceKey(3L)
                    .decisionDefinitionKey(4L)
                    .decisionDefinitionId("ddi")
                    .decisionDefinitionName("ddm")
                    .decisionDefinitionVersion(5)
                    .tenantId("t"))
        .send()
        .join();

    // then
    final DecisionInstanceSearchQuery request =
        gatewayService.getLastRequest(DecisionInstanceSearchQuery.class);
    assertThat(request.getFilter().getDecisionInstanceKey()).isEqualTo("1");
    assertThat(request.getFilter().getState()).isEqualTo(DecisionInstanceStateEnum.FAILED);
    assertThat(request.getFilter().getEvaluationFailure()).isEqualTo("ef");
    assertThat(request.getFilter().getDecisionDefinitionType())
        .isEqualTo(DecisionDefinitionTypeEnum.DECISION_TABLE);
    assertThat(request.getFilter().getProcessDefinitionKey()).isEqualTo("2");
    assertThat(request.getFilter().getProcessInstanceKey()).isEqualTo("3");
    assertThat(request.getFilter().getDecisionDefinitionKey().get$Eq()).isEqualTo("4");
    assertThat(request.getFilter().getDecisionDefinitionId()).isEqualTo("ddi");
    assertThat(request.getFilter().getDecisionDefinitionName()).isEqualTo("ddm");
    assertThat(request.getFilter().getDecisionDefinitionVersion()).isEqualTo(5);
    assertThat(request.getFilter().getTenantId()).isEqualTo("t");
  }

  @Test
  void shouldSearchDecisionInstanceByDecisionDefinitionKeyLongProperty() {
    // when
    client
        .newDecisionInstanceSearchRequest()
        .filter(f -> f.decisionDefinitionKey(b -> b.in(1L, 10L)))
        .send()
        .join();

    // then
    final DecisionInstanceSearchQuery request =
        gatewayService.getLastRequest(DecisionInstanceSearchQuery.class);
    final DecisionInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final BasicStringFilterProperty decisionDefinitionKey = filter.getDecisionDefinitionKey();
    assertThat(decisionDefinitionKey).isNotNull();
    assertThat(decisionDefinitionKey.get$In()).isEqualTo(Arrays.asList("1", "10"));
  }

  @Test
  void shouldSearchDecisionInstanceByEvaluationDateDateTimeProperty() {
    // when
    final OffsetDateTime now = OffsetDateTime.now();
    client
        .newDecisionInstanceSearchRequest()
        .filter(f -> f.evaluationDate(b -> b.neq(now)))
        .send()
        .join();

    // then
    final DecisionInstanceSearchQuery request =
        gatewayService.getLastRequest(DecisionInstanceSearchQuery.class);
    final DecisionInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final DateTimeFilterProperty evaluationDate = filter.getEvaluationDate();
    assertThat(evaluationDate).isNotNull();
    assertThat(evaluationDate.get$Neq()).isEqualTo(now.toString());
  }

  @Test
  void shouldSearchDecisionInstanceWithFullSorting() {
    // when
    client
        .newDecisionInstanceSearchRequest()
        .sort(
            s ->
                s.decisionDefinitionKey()
                    .asc()
                    .decisionDefinitionId()
                    .asc()
                    .decisionDefinitionName()
                    .desc()
                    .processInstanceId()
                    .asc()
                    .evaluationDate()
                    .asc()
                    .evaluationFailure()
                    .asc()
                    .decisionDefinitionVersion()
                    .asc()
                    .state()
                    .asc()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceId()
                    .asc()
                    .decisionDefinitionType()
                    .asc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final DecisionInstanceSearchQuery request =
        gatewayService.getLastRequest(DecisionInstanceSearchQuery.class);
    assertThat(request.getSort().size()).isEqualTo(12);
    assertThat(request.getSort().get(0).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_KEY);
    assertThat(request.getSort().get(0).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(1).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_ID);
    assertThat(request.getSort().get(1).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(2).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_NAME);
    assertThat(request.getSort().get(2).getOrder()).isEqualTo(SortOrderEnum.DESC);
    assertThat(request.getSort().get(3).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.PROCESS_INSTANCE_ID);
    assertThat(request.getSort().get(3).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(4).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.EVALUATION_DATE);
    assertThat(request.getSort().get(4).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(5).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.EVALUATION_FAILURE);
    assertThat(request.getSort().get(5).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(6).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_VERSION);
    assertThat(request.getSort().get(6).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(7).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.STATE);
    assertThat(request.getSort().get(7).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(8).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.PROCESS_DEFINITION_KEY);
    assertThat(request.getSort().get(8).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(9).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.PROCESS_INSTANCE_ID);
    assertThat(request.getSort().get(9).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(10).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.DECISION_DEFINITION_TYPE);
    assertThat(request.getSort().get(10).getOrder()).isEqualTo(SortOrderEnum.ASC);
    assertThat(request.getSort().get(11).getField())
        .isEqualTo(DecisionInstanceSearchQuerySortRequest.FieldEnum.TENANT_ID);
    assertThat(request.getSort().get(11).getOrder()).isEqualTo(SortOrderEnum.ASC);
  }
}
