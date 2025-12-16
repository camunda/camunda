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
package io.camunda.client.incident;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsQuery;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsQueryResult;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.List;
import java.util.Objects;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class IncidentStatisticsTest extends ClientRestTest {

  @Test
  void shouldRequestIncidentProcessInstanceStatistics() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsRequest(
        Instancio.create(IncidentProcessInstanceStatisticsQueryResult.class));

    // when
    client.newIncidentProcessInstanceStatisticsRequest().send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/incidents/statistics/process-instances");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getBodyAsString()).isEqualTo("{\"sort\":[]}");
  }

  @Test
  void shouldRequestIncidentProcessInstanceStatisticsWithPagination() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsRequest(
        Instancio.create(IncidentProcessInstanceStatisticsQueryResult.class));

    // when
    client
        .newIncidentProcessInstanceStatisticsRequest()
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then
    final IncidentProcessInstanceStatisticsQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsQuery.class);
    final OffsetPagination page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isEqualTo(5);
    assertThat(page.getLimit()).isEqualTo(10);
  }

  @Test
  void shouldRequestIncidentProcessInstanceStatisticsWithSorting() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsRequest(
        Instancio.create(IncidentProcessInstanceStatisticsQueryResult.class));

    // when
    client
        .newIncidentProcessInstanceStatisticsRequest()
        .sort(s -> s.errorMessage().asc().activeInstancesWithErrorCount().desc())
        .send()
        .join();

    // then
    final IncidentProcessInstanceStatisticsQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromIncidentProcessInstanceStatisticsQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(2);
    assertSort(sorts.get(0), "errorMessage", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "activeInstancesWithErrorCount", SortOrderEnum.DESC);
  }
}
