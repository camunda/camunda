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
package io.camunda.zeebe.client.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.IncidentFilterRequest;
import io.camunda.zeebe.client.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SearchIncidentTest extends ClientRestTest {

  @Test
  public void shouldSearchIncidentWithEmptyQuery() {
    // when
    client.newIncidentQuery().send().join();

    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchIncidentWithFullFilters() {
    // when
    client
        .newIncidentQuery()
        .filter(
            f ->
                f.key(1L)
                    .processDefinitionKey(2L)
                    .processInstanceKey(3L)
                    .tenantId("tenant")
                    .flowNodeId("flowNode")
                    .flowNodeInstanceId("flowNodeInstance")
                    .jobKey(4L)
                    .state("state")
                    .type("type")
                    .hasActiveOperation(false))
        .send()
        .join();
    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final IncidentFilterRequest filter = request.getFilter();
    assertThat(filter.getKey()).isEqualTo(1L);
    assertThat(filter.getProcessDefinitionKey()).isEqualTo(2L);
    assertThat(filter.getProcessInstanceKey()).isEqualTo(3L);
    assertThat(filter.getTenantId()).isEqualTo("tenant");
    assertThat(filter.getFlowNodeId()).isEqualTo("flowNode");
    assertThat(filter.getFlowNodeInstanceId()).isEqualTo("flowNodeInstance");
    assertThat(filter.getJobKey()).isEqualTo(4L);
    assertThat(filter.getState()).isEqualTo("state");
    assertThat(filter.getType()).isEqualTo("type");
    assertThat(filter.getHasActiveOperation()).isFalse();
  }

  @Test
  void shouldSearchIncidentWithFullSorting() {
    // when
    client
        .newIncidentQuery()
        .sort(
            s ->
                s.key()
                    .asc()
                    .type()
                    .asc()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .asc()
                    .tenantId()
                    .asc()
                    .flowNodeInstanceId()
                    .asc()
                    .flowNodeId()
                    .asc()
                    .state()
                    .asc()
                    .jobKey()
                    .asc()
                    .creationTime()
                    .desc())
        .send()
        .join();

    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final List<SearchQuerySortRequest> sorts = request.getSort();
    assertThat(sorts).hasSize(10);
    assertSort(sorts.get(0), "key", "asc");
    assertSort(sorts.get(1), "type", "asc");
    assertSort(sorts.get(2), "processDefinitionKey", "asc");
    assertSort(sorts.get(3), "processInstanceKey", "asc");
    assertSort(sorts.get(4), "tenantId", "asc");
    assertSort(sorts.get(5), "flowNodeInstanceId", "asc");
    assertSort(sorts.get(6), "flowNodeId", "asc");
    assertSort(sorts.get(7), "state", "asc");
    assertSort(sorts.get(8), "jobKey", "asc");
    assertSort(sorts.get(9), "creationTime", "desc");
  }

  @Test
  void shouldSearchWithFullPagination() {
    // when
    client
        .newIncidentQuery()
        .page(
            p ->
                p.from(23)
                    .limit(5)
                    .searchBefore(Arrays.asList("b"))
                    .searchAfter(Arrays.asList("a")))
        .send()
        .join();

    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest.getFrom()).isEqualTo(23);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getSearchBefore()).isEqualTo(Arrays.asList("b"));
    assertThat(pageRequest.getSearchAfter()).isEqualTo(Arrays.asList("a"));
  }

  private void assertSort(
      final SearchQuerySortRequest sort, final String name, final String order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
