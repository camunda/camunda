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
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class SearchIncidentTest extends ClientRestTest {

  @Test
  void shouldGetIncident() {
    // given
    final long incidentKey = 0xC00L;
    gatewayService.onIncidentRequest(
        incidentKey,
        Instancio.create(IncidentResult.class)
            .incidentKey("1")
            .elementInstanceKey("2")
            .processInstanceKey("3")
            .processDefinitionKey("4")
            .jobKey("5")
            .rootProcessInstanceKey("6")
            .creationTime(OffsetDateTime.now().toString()));

    // when
    client.newIncidentGetRequest(incidentKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/incidents/" + incidentKey);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  public void shouldSearchIncidentWithEmptyQuery() {
    // when
    client.newIncidentSearchRequest().send().join();

    // then
    final IncidentSearchQuery request = gatewayService.getLastRequest(IncidentSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchIncidentWithFullFilters() {
    // when
    client
        .newIncidentSearchRequest()
        .filter(
            f ->
                f.incidentKey(1L)
                    .processDefinitionKey(2L)
                    .processDefinitionId("complexProcess")
                    .processInstanceKey(3L)
                    .errorType(IncidentErrorType.CALLED_DECISION_ERROR)
                    .errorMessage("Can't decide")
                    .elementId("element")
                    .elementInstanceKey(4L)
                    .creationTime(OffsetDateTime.parse("2024-05-23T23:05:00.001Z"))
                    .state(IncidentState.ACTIVE)
                    .jobKey(5L)
                    .tenantId("tenant"))
        .send()
        .join();
    // then
    final IncidentSearchQuery request = gatewayService.getLastRequest(IncidentSearchQuery.class);
    final IncidentFilter filter = request.getFilter();
    assertThat(filter.getIncidentKey().get$Eq()).isEqualTo("1");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo("2");
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("complexProcess");
    assertThat(filter.getProcessInstanceKey().get$Eq()).isEqualTo("3");
    assertThat(filter.getErrorType().get$Eq())
        .isEqualTo(IncidentErrorTypeEnum.CALLED_DECISION_ERROR);
    assertThat(filter.getErrorMessage().get$Eq()).isEqualTo("Can't decide");
    assertThat(filter.getElementId().get$Eq()).isEqualTo("element");
    assertThat(filter.getElementInstanceKey().get$Eq()).isEqualTo("4");
    assertThat(filter.getCreationTime().get$Eq()).isEqualTo("2024-05-23T23:05:00.001Z");
    assertThat(filter.getState().get$Eq()).isEqualTo(IncidentStateEnum.ACTIVE);
    assertThat(filter.getJobKey().get$Eq()).isEqualTo("5");
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant");
  }

  @Test
  void shouldSearchIncidentWithFullSorting() {
    // when
    client
        .newIncidentSearchRequest()
        .sort(
            s ->
                s.incidentKey()
                    .asc()
                    .errorType()
                    .asc()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .asc()
                    .tenantId()
                    .asc()
                    .elementInstanceKey()
                    .asc()
                    .elementId()
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
    final IncidentSearchQuery request = gatewayService.getLastRequest(IncidentSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromIncidentSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(10);
    assertSort(sorts.get(0), "incidentKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "errorType", SortOrderEnum.ASC);
    assertSort(sorts.get(2), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "tenantId", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "elementInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(6), "elementId", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "state", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "jobKey", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "creationTime", SortOrderEnum.DESC);
  }

  /*
   * This test is parameterized to test all possible values of the Zeebe Protocol ErrorType enum.
   * The goal is to catch any Zeebe Protocol error types not implemented in the Java client.
   */
  @ParameterizedTest(name = "Incident for ErrorType: {0}")
  @EnumSource(value = ErrorType.class)
  void shouldSearchIncidentByIncidentErrorType(final ErrorType errorType) {
    // given
    final IncidentErrorType incidentErrorType = IncidentErrorType.valueOf(errorType.name());

    // when
    client
        .newIncidentSearchRequest()
        .filter(f -> f.incidentKey(1L).errorType(incidentErrorType))
        .send()
        .join();

    // then
    final IncidentSearchQuery request = gatewayService.getLastRequest(IncidentSearchQuery.class);
    final IncidentFilter filter = request.getFilter();
    assertThat(filter.getIncidentKey().get$Eq()).isEqualTo("1");
    assertThat(filter.getErrorType().get$Eq())
        .isEqualTo(IncidentErrorTypeEnum.valueOf(errorType.name()));
  }

  @Test
  void shouldSearchIncidentByProcessInstanceKeyWithFullFilters() {
    // when
    client
        .newIncidentsByProcessInstanceSearchRequest(123L)
        .filter(
            f ->
                f.incidentKey(1L)
                    .processDefinitionKey(2L)
                    .processDefinitionId("complexProcess")
                    .errorType(IncidentErrorType.CALLED_DECISION_ERROR)
                    .errorMessage("Can't decide")
                    .elementId("element")
                    .elementInstanceKey(4L)
                    .creationTime(OffsetDateTime.parse("2024-05-23T23:05:00.001Z"))
                    .state(IncidentState.ACTIVE)
                    .jobKey(5L)
                    .tenantId("tenant"))
        .send()
        .join();
    // then
    final IncidentSearchQuery request = gatewayService.getLastRequest(IncidentSearchQuery.class);
    final IncidentFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getIncidentKey().get$Eq()).isEqualTo("1");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo("2");
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("complexProcess");
    assertThat(filter.getErrorType().get$Eq())
        .isEqualTo(IncidentErrorTypeEnum.CALLED_DECISION_ERROR);
    assertThat(filter.getErrorMessage().get$Eq()).isEqualTo("Can't decide");
    assertThat(filter.getElementId().get$Eq()).isEqualTo("element");
    assertThat(filter.getElementInstanceKey().get$Eq()).isEqualTo("4");
    assertThat(filter.getCreationTime().get$Eq()).isEqualTo("2024-05-23T23:05:00.001Z");
    assertThat(filter.getState().get$Eq()).isEqualTo(IncidentStateEnum.ACTIVE);
    assertThat(filter.getJobKey().get$Eq()).isEqualTo("5");
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant");
  }
}
