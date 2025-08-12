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
package io.camunda.client.elementinstance;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class ElementInstanceTest extends ClientRestTest {
  @Test
  void shouldSearchElementInstance() {
    // when
    client.newElementInstanceSearchRequest().send().join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchElementInstanceWithFullFilters() {
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(
            f ->
                f.elementInstanceKey(1L)
                    .type(ElementInstanceType.SERVICE_TASK)
                    .state(ElementInstanceState.ACTIVE)
                    .processDefinitionKey(2L)
                    .processDefinitionId("complexProcess")
                    .processInstanceKey(3L)
                    .elementId("elementId")
                    .hasIncident(true)
                    .incidentKey(4L)
                    .tenantId("<default>")
                    .startDate(b -> b.exists(true))
                    .endDate(b -> b.exists(true))
                    .elementInstanceScopeKey(4L))
        .send()
        .join();
    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getElementInstanceKey()).isEqualTo("1");
    assertThat(filter.getType()).isEqualTo(ElementInstanceFilter.TypeEnum.SERVICE_TASK);
    assertThat(filter.getState().get$Eq()).isEqualTo(ElementInstanceStateEnum.ACTIVE);
    assertThat(filter.getProcessDefinitionKey()).isEqualTo("2");
    assertThat(filter.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(filter.getProcessInstanceKey()).isEqualTo("3");
    assertThat(filter.getElementId()).isEqualTo("elementId");
    assertThat(filter.getHasIncident()).isTrue();
    assertThat(filter.getIncidentKey()).isEqualTo("4");
    assertThat(filter.getTenantId()).isEqualTo("<default>");
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Exists()).isTrue();
    assertThat(filter.getEndDate().get$Exists()).isTrue();
    assertThat(filter.getElementInstanceScopeKey()).isEqualTo("4");
  }

  @Test
  void shouldReturnElementInstanceByDatesNotExists() {
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.exists(false)).endDate(b -> b.exists(false)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Exists()).isFalse();
    assertThat(filter.getEndDate().get$Exists()).isFalse();
  }

  @Test
  void shouldReturnElementInstanceByDatesGt() {
    final OffsetDateTime now = OffsetDateTime.now();
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.gt(now)).endDate(b -> b.gt(now)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Gt()).isNotNull();
    assertThat(filter.getEndDate().get$Gt()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesLt() {
    final OffsetDateTime now = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.lt(now)).endDate(b -> b.lt(now)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Lt()).isNotNull();
    assertThat(filter.getEndDate().get$Lt()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesGte() {
    final OffsetDateTime now = OffsetDateTime.now();
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.gte(now)).endDate(b -> b.gte(now)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Gte()).isNotNull();
    assertThat(filter.getEndDate().get$Gte()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesLte() {
    final OffsetDateTime now = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.lte(now)).endDate(b -> b.lte(now)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Lte()).isNotNull();
    assertThat(filter.getEndDate().get$Lte()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesGteLte() {
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.gte(startDate)).endDate(b -> b.lte(endDate)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Gte()).isNotNull();
    assertThat(filter.getEndDate().get$Lte()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesGtLt() {
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.gt(startDate)).endDate(b -> b.lt(endDate)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Gt()).isNotNull();
    assertThat(filter.getEndDate().get$Lt()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesEq() {
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.eq(startDate)).endDate(b -> b.eq(endDate)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Eq()).isNotNull();
    assertThat(filter.getEndDate().get$Eq()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesNeq() {
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(f -> f.startDate(b -> b.neq(startDate)).endDate(b -> b.neq(endDate)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$Neq()).isNotNull();
    assertThat(filter.getEndDate().get$Neq()).isNotNull();
  }

  @Test
  void shouldReturnElementInstanceByDatesIn() {
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    // when
    client
        .newElementInstanceSearchRequest()
        .filter(
            f -> f.startDate(b -> b.in(startDate, endDate)).endDate(b -> b.in(startDate, endDate)))
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final ElementInstanceFilter filter = Objects.requireNonNull(request.getFilter());
    assertThat(filter.getStartDate()).isNotNull();
    assertThat(filter.getEndDate()).isNotNull();
    assertThat(filter.getStartDate().get$In()).isNotNull();
    assertThat(filter.getEndDate().get$In()).isNotNull();
  }

  @Test
  void shouldSearchElementInstanceWithFullSorting() {
    // when
    client
        .newElementInstanceSearchRequest()
        .sort(
            s ->
                s.elementInstanceKey()
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
                    .asc()
                    .startDate()
                    .asc()
                    .endDate()
                    .asc())
        .send()
        .join();

    // then
    final ElementInstanceSearchQuery request =
        gatewayService.getLastRequest(ElementInstanceSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromElementInstanceSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts.size()).isEqualTo(11);
    assertSort(sorts.get(0), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(2), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "type", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "state", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "startDate", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "endDate", SortOrderEnum.DESC);
    assertSort(sorts.get(7), "incidentKey", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "tenantId", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "startDate", SortOrderEnum.ASC);
    assertSort(sorts.get(10), "endDate", SortOrderEnum.ASC);
  }

  @Test
  public void shouldGetElementInstance() {
    // when
    final long elementInstanceKey = 0xC00L;
    client.newElementInstanceGetRequest(elementInstanceKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/element-instances/" + elementInstanceKey);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }
}
