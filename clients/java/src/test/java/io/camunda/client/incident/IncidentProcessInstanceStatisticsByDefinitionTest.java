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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionResult;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IncidentProcessInstanceStatisticsByDefinitionTest extends ClientRestTest {

  private static final Integer ERROR_HASH_CODE = 1234567890;

  @BeforeEach
  void setup() {
    gatewayService.onIncidentProcessInstanceStatisticsByDefinitionRequest(
        Instancio.create(IncidentProcessInstanceStatisticsByDefinitionQueryResult.class)
            .items(getIncidentProcessInstanceStatisticsByDefinitionResults()));
  }

  @Test
  void shouldRetrieveIncidentProcessInstanceStatisticsByDefinitionRequest()
      throws JsonProcessingException {
    // when
    client.newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_HASH_CODE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/incidents/statistics/process-instances-by-definition");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode json = mapper.readTree(request.getBodyAsString());

    AssertionsForClassTypes.assertThat(json.get("filter")).isNotNull();
    AssertionsForClassTypes.assertThat(json.get("filter").get("errorHashCode").asInt())
        .isEqualTo(ERROR_HASH_CODE);
    AssertionsForClassTypes.assertThat(json.get("page")).isNull();
    AssertionsForClassTypes.assertThat(json.get("sort")).isNotNull();
    AssertionsForClassTypes.assertThat(json.get("sort").isArray()).isTrue();
    Assertions.assertThat(json.get("sort")).isEmpty();
  }

  @Test
  void shouldRetrieveIncidentProcessInstanceStatisticsByDefinitionWithOffsetPagination() {
    // when
    client
        .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_HASH_CODE)
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then
    final IncidentProcessInstanceStatisticsByDefinitionQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsByDefinitionQuery.class);

    final OffsetPagination page = request.getPage();
    AssertionsForClassTypes.assertThat(page).isNotNull();
    AssertionsForClassTypes.assertThat(page).extracting(OffsetPagination::getFrom).isEqualTo(5);

    AssertionsForClassTypes.assertThat(page).extracting(OffsetPagination::getLimit).isEqualTo(10);
  }

  @Test
  void shouldRetrieveIncidentProcessInstanceStatisticsByDefinitionWithSorting() {
    // when
    client
        .newIncidentProcessInstanceStatisticsByDefinitionRequest(ERROR_HASH_CODE)
        .sort(
            s ->
                s.processDefinitionKey()
                    .asc()
                    .processDefinitionId()
                    .desc()
                    .processDefinitionName()
                    .asc()
                    .processDefinitionVersion()
                    .desc()
                    .tenantId()
                    .asc()
                    .activeInstancesWithErrorCount()
                    .desc())
        .send()
        .join();

    // then
    final IncidentProcessInstanceStatisticsByDefinitionQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsByDefinitionQuery.class);

    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromIncidentProcessInstanceStatisticsByDefinitionQuerySortRequest(
            Objects.requireNonNull(request.getSort()));

    Assertions.assertThat(sorts).hasSize(6);
    assertSort(sorts.get(0), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processDefinitionId", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "processDefinitionName", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "processDefinitionVersion", SortOrderEnum.DESC);
    assertSort(sorts.get(4), "tenantId", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "activeInstancesWithErrorCount", SortOrderEnum.DESC);
  }

  private static List<IncidentProcessInstanceStatisticsByDefinitionResult>
      getIncidentProcessInstanceStatisticsByDefinitionResults() {
    final IncidentProcessInstanceStatisticsByDefinitionResult item =
        Instancio.create(IncidentProcessInstanceStatisticsByDefinitionResult.class);
    item.setProcessDefinitionKey("12345");
    final List<IncidentProcessInstanceStatisticsByDefinitionResult> resultList = new ArrayList<>();
    resultList.add(item);
    return resultList;
  }
}
