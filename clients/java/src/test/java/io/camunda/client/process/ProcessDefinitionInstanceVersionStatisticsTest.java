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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessDefinitionInstanceVersionStatisticsResult;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionInstanceVersionStatisticsTest extends ClientRestTest {

  private static final String PROCESS_DEFINITION_ID = "order_process";

  @BeforeEach
  void setup() {
    gatewayService.onProcessDefinitionInstanceVersionStatisticsRequest(
        Instancio.create(ProcessDefinitionInstanceVersionStatisticsQueryResult.class)
            .items(getProcessDefinitionInstanceVersionStatisticsResults()));
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceVersionStatistics() throws JsonProcessingException {
    // when
    client
        .newProcessDefinitionInstanceVersionStatisticsRequest(PROCESS_DEFINITION_ID)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/process-definitions/statistics/process-instances-by-version");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode json = mapper.readTree(request.getBodyAsString());

    assertThat(json.get("filter")).isNotNull();
    assertThat(json.get("filter").get("processDefinitionId").asText())
        .isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(json.get("page")).isNull();
    assertThat(json.get("sort")).isNotNull();
    assertThat(json.get("sort").isArray()).isTrue();
    Assertions.assertThat(json.get("sort")).isEmpty();
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceVersionStatisticsWithFullFilter() {
    // when
    client
        .newProcessDefinitionInstanceVersionStatisticsRequest(PROCESS_DEFINITION_ID)
        .filter(f -> f.tenantId("tenant-a"))
        .send()
        .join();

    // then
    final ProcessDefinitionInstanceVersionStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionInstanceVersionStatisticsQuery.class);

    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter())
        .extracting(ProcessDefinitionInstanceVersionStatisticsFilter::getProcessDefinitionId)
        .isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(request.getFilter())
        .extracting(ProcessDefinitionInstanceVersionStatisticsFilter::getTenantId)
        .isEqualTo("tenant-a");
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceVersionStatisticsWithOffsetPagination() {
    // when
    client
        .newProcessDefinitionInstanceVersionStatisticsRequest(PROCESS_DEFINITION_ID)
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then
    final ProcessDefinitionInstanceVersionStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionInstanceVersionStatisticsQuery.class);

    final OffsetPagination page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page).extracting(OffsetPagination::getFrom).isEqualTo(5);
    assertThat(page).extracting(OffsetPagination::getLimit).isEqualTo(10);
  }

  @Test
  void shouldRetrieveProcessDefinitionInstanceVersionStatisticsWithSorting() {
    // when
    client
        .newProcessDefinitionInstanceVersionStatisticsRequest(PROCESS_DEFINITION_ID)
        .sort(
            s ->
                s.processDefinitionId()
                    .asc()
                    .processDefinitionVersion()
                    .desc()
                    .processDefinitionKey()
                    .asc()
                    .processDefinitionName()
                    .desc()
                    .activeInstancesWithIncidentCount()
                    .asc()
                    .activeInstancesWithoutIncidentCount()
                    .desc())
        .send()
        .join();

    // then
    final ProcessDefinitionInstanceVersionStatisticsQuery request =
        gatewayService.getLastRequest(ProcessDefinitionInstanceVersionStatisticsQuery.class);

    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromProcessDefinitionInstanceVersionStatisticsQuerySortRequest(
            Objects.requireNonNull(request.getSort()));

    Assertions.assertThat(sorts).hasSize(6);
    assertSort(sorts.get(0), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processDefinitionVersion", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "processDefinitionName", SortOrderEnum.DESC);
    assertSort(sorts.get(4), "activeInstancesWithIncidentCount", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "activeInstancesWithoutIncidentCount", SortOrderEnum.DESC);
  }

  private static List<ProcessDefinitionInstanceVersionStatisticsResult>
      getProcessDefinitionInstanceVersionStatisticsResults() {
    final ProcessDefinitionInstanceVersionStatisticsResult item =
        Instancio.create(ProcessDefinitionInstanceVersionStatisticsResult.class);
    item.setProcessDefinitionKey("12345");
    final List<ProcessDefinitionInstanceVersionStatisticsResult> resultList = new ArrayList<>();
    resultList.add(item);
    return resultList;
  }
}
