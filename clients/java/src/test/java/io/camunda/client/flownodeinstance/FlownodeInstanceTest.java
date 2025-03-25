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
    assertThat(filter.getState()).isEqualTo(FlowNodeInstanceStateEnum.ACTIVE);
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

  @Test
  public void shouldConvertFlowNodeInstanceType() {

    for (final FlowNodeInstanceType value : FlowNodeInstanceType.values()) {
      final FlowNodeInstanceFilter.TypeEnum protocolValue =
          FlowNodeInstanceType.toProtocolType(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceType.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue)
            .isEqualTo(FlowNodeInstanceFilter.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final FlowNodeInstanceResult.TypeEnum protocolValue :
        FlowNodeInstanceResult.TypeEnum.values()) {
      final FlowNodeInstanceType value = FlowNodeInstanceType.fromProtocolType(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue == FlowNodeInstanceResult.TypeEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceType.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  @Test
  public void shouldConvertFlowNodeInstanceState() {

    for (final FlowNodeInstanceState value : FlowNodeInstanceState.values()) {
      final FlowNodeInstanceStateEnum protocolValue = FlowNodeInstanceState.toProtocolState(value);
      assertThat(protocolValue).isNotNull();
      if (value == FlowNodeInstanceState.UNKNOWN_ENUM_VALUE) {
        assertThat(protocolValue).isEqualTo(FlowNodeInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API);
      } else {
        assertThat(protocolValue.name()).isEqualTo(value.name());
      }
    }

    for (final FlowNodeInstanceStateEnum protocolValue : FlowNodeInstanceStateEnum.values()) {
      final FlowNodeInstanceState value = FlowNodeInstanceState.fromProtocolState(protocolValue);
      assertThat(value).isNotNull();
      if (protocolValue == FlowNodeInstanceStateEnum.UNKNOWN_DEFAULT_OPEN_API) {
        assertThat(value).isEqualTo(FlowNodeInstanceState.UNKNOWN_ENUM_VALUE);
      } else {
        assertThat(value.name()).isEqualTo(protocolValue.name());
      }
    }
  }

  private void assertSort(
      final SearchRequestSort sort, final String field, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(field);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
