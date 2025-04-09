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

import static io.camunda.client.api.search.enums.ProcessInstanceState.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.FlowNodeInstanceState;
import io.camunda.client.api.search.filter.ProcessInstanceFilterBase;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
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
    client.newProcessInstanceSearchRequest().send().join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchProcessInstanceWithFullFilters() {
    // when
    final OffsetDateTime startDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endDate = OffsetDateTime.now();
    final Map<String, Object> variablesMap = new LinkedHashMap<>();
    variablesMap.put("n1", "v1");
    variablesMap.put("n2", "v2");
    final List<ProcessInstanceVariableFilterRequest> variables =
        Arrays.asList(
            new ProcessInstanceVariableFilterRequest()
                .name("n1")
                .value(new StringFilterProperty().$eq("v1")),
            new ProcessInstanceVariableFilterRequest()
                .name("n2")
                .value(new StringFilterProperty().$eq("v2")));
    client
        .newProcessInstanceSearchRequest()
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
                    .state(ACTIVE)
                    .hasIncident(true)
                    .tenantId("tenant")
                    .variables(variablesMap)
                    .errorMessage("Error message")
                    .hasRetriesLeft(true)
                    .flowNodeId("flowNodeId")
                    .flowNodeInstanceState(FlowNodeInstanceState.ACTIVE)
                    .hasFlowNodeInstanceIncident(true)
                    .incidentErrorHashCode(123456789))
        .send()
        .join();
    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getProcessInstanceKey().get$Eq()).isEqualTo("123");
    assertThat(filter.getProcessDefinitionId().get$Eq()).isEqualTo("bpmnProcessId");
    assertThat(filter.getProcessDefinitionName().get$Eq()).isEqualTo("Demo process");
    assertThat(filter.getProcessDefinitionVersion().get$Eq()).isEqualTo(7);
    assertThat(filter.getProcessDefinitionVersionTag().get$Eq()).isEqualTo("v7");
    assertThat(filter.getProcessDefinitionKey().get$Eq()).isEqualTo("15");
    assertThat(filter.getParentProcessInstanceKey().get$Eq()).isEqualTo("25");
    assertThat(filter.getParentElementInstanceKey().get$Eq()).isEqualTo("30");
    assertThat(filter.getStartDate().get$Eq()).isEqualTo(startDate.toString());
    assertThat(filter.getEndDate().get$Eq()).isEqualTo(endDate.toString());
    assertThat(filter.getState().get$Eq())
        .isEqualTo(io.camunda.client.protocol.rest.ProcessInstanceStateEnum.ACTIVE);
    assertThat(filter.getHasIncident()).isEqualTo(true);
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant");
    assertThat(filter.getVariables()).isEqualTo(variables);
    assertThat(filter.getErrorMessage().get$Eq()).isEqualTo("Error message");
    assertThat(filter.getHasRetriesLeft()).isEqualTo(true);
    assertThat(filter.getElementId().get$Eq()).isEqualTo("flowNodeId");
    assertThat(filter.getElementInstanceState().get$Eq())
        .isEqualTo(ElementInstanceStateEnum.ACTIVE);
    assertThat(filter.getHasElementInstanceIncident()).isEqualTo(true);
    assertThat(filter.getIncidentErrorHashCode()).isEqualTo(123456789);
  }

  @Test
  void shouldSearchProcessInstanceByProcessInstanceKeyLongInFilter() {
    // when
    client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processInstanceKey(b -> b.in(1L, 10L)))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final BasicStringFilterProperty processInstanceKey = filter.getProcessInstanceKey();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$In()).isEqualTo(Arrays.asList("1", "10"));
  }

  @Test
  void shouldSearchProcessInstanceByProcessInstanceKeyLongNinFilter() {
    // when
    client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processInstanceKey(b -> b.notIn(1L, 10L)))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final BasicStringFilterProperty processInstanceKey = filter.getProcessInstanceKey();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$NotIn()).isEqualTo(Arrays.asList("1", "10"));
  }

  @Test
  void shouldSearchProcessInstanceByProcessDefinitionIdStringFilter() {
    // when
    client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(b -> b.like("string")))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final StringFilterProperty processInstanceKey = filter.getProcessDefinitionId();
    assertThat(processInstanceKey).isNotNull();
    assertThat(processInstanceKey.get$Like()).isEqualTo("string");
  }

  @Test
  void shouldSearchProcessInstanceByStartDateDateTimeFilter() {
    // when
    final OffsetDateTime now = OffsetDateTime.now();
    client.newProcessInstanceSearchRequest().filter(f -> f.startDate(b -> b.gt(now))).send().join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    final DateTimeFilterProperty startDate = filter.getStartDate();
    assertThat(startDate).isNotNull();
    assertThat(startDate.get$Gt()).isEqualTo(now.toString());
  }

  @Test
  void shouldSearchProcessInstanceByVariablesFilter() {
    // given
    final Map<String, Object> variablesMap = new LinkedHashMap<>();
    variablesMap.put("n1", "v1");
    variablesMap.put("n2", "v2");
    final List<ProcessInstanceVariableFilterRequest> variables =
        Arrays.asList(
            new ProcessInstanceVariableFilterRequest()
                .name("n1")
                .value(new StringFilterProperty().$eq("v1")),
            new ProcessInstanceVariableFilterRequest()
                .name("n2")
                .value(new StringFilterProperty().$eq("v2")));

    // when
    client.newProcessInstanceSearchRequest().filter(f -> f.variables(variablesMap)).send().join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldSearchProcessInstanceWithFullSorting() {
    // when
    client
        .newProcessInstanceSearchRequest()
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
                    .hasIncident()
                    .desc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromProcessInstanceSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(13);
    assertSort(sorts.get(0), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processDefinitionId", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "processDefinitionName", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "processDefinitionVersion", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "processDefinitionVersionTag", SortOrderEnum.DESC);
    assertSort(sorts.get(5), "processDefinitionKey", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "parentProcessInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "parentFlowNodeInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "startDate", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "endDate", SortOrderEnum.ASC);
    assertSort(sorts.get(10), "state", SortOrderEnum.ASC);
    assertSort(sorts.get(11), "hasIncident", SortOrderEnum.DESC);
    assertSort(sorts.get(12), "tenantId", SortOrderEnum.ASC);
  }

  @Test
  void shouldSearchWithFullPagination() {
    // when
    client
        .newProcessInstanceSearchRequest()
        .page(
            p ->
                p.from(23)
                    .limit(5)
                    .searchBefore(Collections.singletonList("b"))
                    .searchAfter(Collections.singletonList("a")))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(23);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getSearchBefore()).isEqualTo(Collections.singletonList("b"));
    assertThat(pageRequest.getSearchAfter()).isEqualTo(Collections.singletonList("a"));
  }

  @Test
  void shouldSearchProcessInstanceWithOrOperatorFilters() {
    // when
    final OffsetDateTime now = OffsetDateTime.now();

    client
        .newProcessInstanceSearchRequest()
        .filter(
            f ->
                f.tenantId("tenant-1")
                    .orFilters(Arrays.asList(f1 -> f1.state(ACTIVE), f3 -> f3.hasIncident(true))))
        .send()
        .join();

    // then
    final ProcessInstanceSearchQuery request =
        gatewayService.getLastRequest(ProcessInstanceSearchQuery.class);
    final ProcessInstanceFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getTenantId().get$Eq()).isEqualTo("tenant-1");
    assertThat(filter.get$Or()).isNotNull();
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or().get(0).getState().get$Eq())
        .isEqualTo(ProcessInstanceStateEnum.ACTIVE);
    assertThat(filter.get$Or().get(1).getHasIncident()).isTrue();
  }

  private void assertSort(
      final SearchRequestSort sort, final String name, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }

  @Test
  void shouldHaveMatchingFilterMethodsInBaseAndFullInterfaces() {
    final Set<String> baseMethods = publicMethodSignatures(ProcessInstanceFilterBase.class);
    final Set<String> fullMethods =
        publicMethodSignatures(io.camunda.client.api.search.filter.ProcessInstanceFilter.class);

    // Full interface must contain all base interface methods
    assertThat(fullMethods)
        .withFailMessage("Full interface is missing methods from base interface")
        .containsAll(baseMethods);

    // Full interface may only include orFilters in addition to base methods
    final Set<String> expectedExtras = new HashSet<>();
    expectedExtras.add("orFilters[interface java.util.List]");
    final Set<String> actualExtras = new HashSet<>(fullMethods);
    actualExtras.removeAll(baseMethods);

    assertThat(actualExtras)
        .withFailMessage("Unexpected methods in full interface: %s", actualExtras)
        .isEqualTo(expectedExtras);
  }

  private static Set<String> publicMethodSignatures(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
        .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
        .collect(Collectors.toSet());
  }
}
