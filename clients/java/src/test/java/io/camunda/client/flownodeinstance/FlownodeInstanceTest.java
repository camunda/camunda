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
package io.camunda.client.flownodeinstance;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.enums.FlowNodeInstanceType;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class FlownodeInstanceTest extends ClientRestTest {
  @Test
  void shouldSearchFlownodeInstance() {
    // when
    client.newFlownodeInstanceSearchRequest().send().join();

    // then
    final FlowNodeInstanceSearchQuery request =
        gatewayService.getLastRequest(FlowNodeInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchFlownodeInstanceWithFullFilters() {
    // when
    client
        .newFlownodeInstanceSearchRequest()
        .filter(
            f ->
                f.flowNodeInstanceKey(1L)
                    .type(FlowNodeInstanceType.SERVICE_TASK)
                    .state(FlowNodeInstanceState.ACTIVE)
                    .processDefinitionKey(2L)
                    .processDefinitionId("complexProcess")
                    .processInstanceKey(3L)
                    .flowNodeId("flowNodeId")
                    .hasIncident(true)
                    .incidentKey(4L)
                    .tenantId("<default>"))
        .send()
        .join();
    // then
    final FlowNodeInstanceSearchQuery request =
        gatewayService.getLastRequest(FlowNodeInstanceSearchQuery.class);
    final FlowNodeInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getFlowNodeInstanceKey()).isEqualTo("1");
    assertThat(filter.getType()).isEqualTo(FlowNodeInstanceFilter.TypeEnum.SERVICE_TASK);
    assertThat(filter.getState().get$Eq()).isEqualTo(FlowNodeInstanceStateEnum.ACTIVE);
    assertThat(filter.getProcessDefinitionKey()).isEqualTo("2");
    assertThat(filter.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(filter.getProcessInstanceKey()).isEqualTo("3");
    assertThat(filter.getFlowNodeId()).isEqualTo("flowNodeId");
    assertThat(filter.getHasIncident()).isTrue();
    assertThat(filter.getIncidentKey()).isEqualTo("4");
    assertThat(filter.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldSearchFlownodeInstanceWithFullSorting() {
    // when
    client
        .newFlownodeInstanceSearchRequest()
        .sort(
            s ->
                s.flowNodeInstanceKey()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .asc()
                    .processDefinitionId()
                    .asc()
                    .type()
                    .asc()
                    .state()
                    .asc()
                    .startDate()
                    .desc()
                    .endDate()
                    .desc()
                    .incidentKey()
                    .asc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final FlowNodeInstanceSearchQuery request =
        gatewayService.getLastRequest(FlowNodeInstanceSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromFlowNodeInstanceSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts.size()).isEqualTo(9);
    assertSort(sorts.get(0), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(2), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "type", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "state", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "startDate", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "endDate", SortOrderEnum.DESC);
    assertSort(sorts.get(7), "incidentKey", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "tenantId", SortOrderEnum.ASC);
  }

  @Test
  public void shouldGetFlownodeInstance() {
    // when
    final long flowNodeInstanceKey = 0xC00L;
    client.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/flownode-instances/" + flowNodeInstanceKey);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  private void assertSort(
      final SearchRequestSort sort, final String field, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(field);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
