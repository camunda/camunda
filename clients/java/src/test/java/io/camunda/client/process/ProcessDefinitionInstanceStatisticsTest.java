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
package io.camunda.client.process;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionInstanceStatisticsTest extends ClientRestTest {

  @Test
  void shouldRetrieveProcessDefinitionInstanceStatistics() {
    // given
    gatewayService.onProcessDefinitionInstanceStatisticsRequest(
        Instancio.create(ProcessDefinitionInstanceStatisticsQueryResult.class));

    // when
    client.newProcessDefinitionInstanceStatisticsRequest().send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/process-definitions/statistics/process-instances");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    Assertions.assertThat(request.getBodyAsString()).isEqualTo("{\"sort\":[]}");
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceStatisticsWithOffsetPagination() {
    // given
    gatewayService.onProcessDefinitionInstanceStatisticsRequest(
        Instancio.create(ProcessDefinitionInstanceStatisticsQueryResult.class));

    // when
    client
        .newProcessDefinitionInstanceStatisticsRequest()
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then
    final ProcessDefinitionInstanceStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionInstanceStatisticsQuery.class);

    final OffsetPagination page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page).extracting(OffsetPagination::getFrom).isEqualTo(5);
    assertThat(page).extracting(OffsetPagination::getLimit).isEqualTo(10);
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceStatisticsWithSorting() {
    // given
    gatewayService.onProcessDefinitionInstanceStatisticsRequest(
        Instancio.create(ProcessDefinitionInstanceStatisticsQueryResult.class));

    // when
    client
        .newProcessDefinitionInstanceStatisticsRequest()
        .sort(
            s ->
                s.processDefinitionId()
                    .asc()
                    .activeInstancesWithIncidentCount()
                    .desc()
                    .activeInstancesWithoutIncidentCount()
                    .asc())
        .send()
        .join();

    // then
    final ProcessDefinitionInstanceStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionInstanceStatisticsQuery.class);

    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromProcessDefinitionInstanceStatisticsQuerySortRequest(
            Objects.requireNonNull(request.getSort()));

    Assertions.assertThat(sorts).hasSize(3);
    assertSort(sorts.get(0), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "activeInstancesWithIncidentCount", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "activeInstancesWithoutIncidentCount", SortOrderEnum.ASC);
  }
}
