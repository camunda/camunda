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
package io.camunda.zeebe.client.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.zeebe.client.protocol.rest.*;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class QueryProcessInstanceTest extends ClientRestTest {

  @Test
  public void shouldGetProcessInstanceByKey() {
    // when
    client.newProcessInstanceGetRequest(123L).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/process-instances/123");
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  public void shouldSearchProcessInstanceWithEmptyQuery() {
    // when
    client.newProcessInstanceQuery().send().join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchProcessInstanceWithFullFilters() {
    // when
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    final List<ProcessInstanceVariableFilterRequest> variables =
        Arrays.asList(
            new ProcessInstanceVariableFilterRequest().name("n1").value("v1"),
            new ProcessInstanceVariableFilterRequest().name("n2").value("v2"));
    client
        .newProcessInstanceQuery()
        .filter(
            f ->
                f.processInstanceKey(123L)
                    .processDefinitionId("bpmnProcessId")
                    .processDefinitionName("Demo process")
                    .processDefinitionVersion(7)
                    .processDefinitionVersionTag("v7")
                    .processDefinitionKey(15L)
                    .parentProcessInstanceKey(25L)
                    .parentFlowNodeInstanceKey(30L)
                    .startDate(startDate)
                    .endDate(endDate)
                    .state("ACTIVE")
                    .hasIncident(true)
                    .tenantId("tenant")
                    .variables(variables))
        .send()
        .join();
    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getProcessInstanceKey().get$Eq()).isEqualTo(123L);
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("bpmnProcessId");
    assertThat(filter.getProcessDefinitionName().get$Eq()).isEqualTo("Demo process");
    assertThat(filter.getProcessDefinitionVersion().get$Eq()).isEqualTo(7);
    assertThat(filter.getProcessDefinitionVersionTag().get$Eq()).isEqualTo("v7");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo(15L);
    assertThat(filter.getParentProcessInstanceKey().get$Eq()).isEqualTo(25L);
    assertThat(filter.getParentFlowNodeInstanceKey().get$Eq()).isEqualTo(30L);
    assertThat(filter.getStartDate().get$Eq()).isEqualTo(startDate.toString());
    assertThat(filter.getEndDate().get$Eq()).isEqualTo(endDate.toString());
    assertThat(filter.getState().get$Eq()).isEqualTo(ProcessInstanceStateEnum.ACTIVE);
    assertThat(filter.getHasIncident()).isEqualTo(true);
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant");
    assertThat(filter.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldSearchProcessInstanceByProcessInstanceKeyLongFilter() {
    // when
    client
        .newProcessInstanceQuery()
        .filter(f -> f.processInstanceKey(b -> b.gt(1L).lt(10L)))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    final LongFilterProperty processInstanceKey = filter.getProcessInstanceKey();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$Gt()).isEqualTo(1);
    assertThat(processInstanceKey.get$Lt()).isEqualTo(10);
  }

  @Test
  void shouldSearchProcessInstanceByProcessDefinitionIdStringFilter() {
    // when
    client
        .newProcessInstanceQuery()
        .filter(f -> f.processDefinitionId(b -> b.like("string")))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    final StringFilterProperty processInstanceKey = filter.getProcessDefinitionId();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$Like()).isEqualTo("string");
  }

  @Test
  void shouldSearchProcessInstanceByStartDateDateTimeFilter() {
    // when
    final OffsetDateTime now = OffsetDateTime.now();
    client.newProcessInstanceQuery().filter(f -> f.startDate(b -> b.gt(now))).send().join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    final DateTimeFilterProperty startDate = filter.getStartDate();
    assertThat(startDate).isNotNull();
    assertThat(startDate.get$Gt()).isEqualTo(now.toString());
  }

  @Test
  void shouldSearchProcessInstanceByVariablesFilter() {
    // given
    final List<ProcessInstanceVariableFilterRequest> variables =
        Arrays.asList(
            new ProcessInstanceVariableFilterRequest().name("n1").value("v1"),
            new ProcessInstanceVariableFilterRequest().name("n2").value("v2"));

    // when
    client.newProcessInstanceQuery().filter(f -> f.variables(variables)).send().join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldSearchProcessInstanceWithFullSorting() {
    // when
    client
        .newProcessInstanceQuery()
        .sort(
            s ->
                s.processInstanceKey()
                    .asc()
                    .processDefinitionId()
                    .desc()
                    .processDefinitionName()
                    .asc()
                    .processDefinitionVersion()
                    .asc()
                    .processDefinitionVersionTag()
                    .desc()
                    .processDefinitionKey()
                    .desc()
                    .parentProcessInstanceKey()
                    .asc()
                    .parentFlowNodeInstanceKey()
                    .asc()
                    .startDate()
                    .asc()
                    .endDate()
                    .asc()
                    .state()
                    .asc()
                    .incident()
                    .desc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final List<SearchQuerySortRequest> sorts = request.getSort();
    assertThat(sorts).hasSize(13);
    assertSort(sorts.get(0), "key", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "bpmnProcessId", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "processName", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "processVersion", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "processVersionTag", SortOrderEnum.DESC);
    assertSort(sorts.get(5), "processDefinitionKey", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "parentProcessInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "parentFlowNodeInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "startDate", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "endDate", SortOrderEnum.ASC);
    assertSort(sorts.get(10), "state", SortOrderEnum.ASC);
    assertSort(sorts.get(11), "incident", SortOrderEnum.DESC);
    assertSort(sorts.get(12), "tenantId", SortOrderEnum.ASC);
  }

  @Test
  void shouldSearchWithFullPagination() {
    // when
    client
        .newProcessInstanceQuery()
        .page(
            p ->
                p.from(23)
                    .limit(5)
                    .searchBefore(Collections.singletonList("b"))
                    .searchAfter(Collections.singletonList("a")))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(23);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getSearchBefore()).isEqualTo(Collections.singletonList("b"));
    assertThat(pageRequest.getSearchAfter()).isEqualTo(Collections.singletonList("a"));
  }

  private void assertSort(
      final SearchQuerySortRequest sort, final String name, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
