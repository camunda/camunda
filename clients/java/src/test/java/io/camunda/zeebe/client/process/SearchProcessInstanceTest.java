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

import io.camunda.zeebe.client.protocol.rest.*;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SearchProcessInstanceTest extends ClientRestTest {

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
    client
        .newProcessInstanceQuery()
        .filter(
            f ->
                f.running(true)
                    .active(false)
                    .incidents(true)
                    .finished(false)
                    .completed(false)
                    .canceled(true)
                    .retriesLeft(true)
                    .errorMessage("error")
                    .activityId("activity")
                    .startDate("startDate")
                    .endDate("endDate")
                    .bpmnProcessId("bpmnProcessId")
                    .processDefinitionVersion(3)
                    .variable(
                        new ProcessInstanceVariableFilterRequest()
                            .name("varName")
                            .addValuesItem("val1"))
                    .batchOperationId("batchOperationId")
                    .parentProcessInstanceKey(1234L)
                    .tenantId("tenant"))
        .send()
        .join();
    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final ProcessInstanceFilterRequest filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getRunning()).isEqualTo(true);
    assertThat(filter.getActive()).isEqualTo(false);
    assertThat(filter.getIncidents()).isEqualTo(true);
    assertThat(filter.getFinished()).isEqualTo(false);
    assertThat(filter.getCompleted()).isEqualTo(false);
    assertThat(filter.getCanceled()).isEqualTo(true);
    assertThat(filter.getRetriesLeft()).isEqualTo(true);
    assertThat(filter.getErrorMessage()).isEqualTo("error");
    assertThat(filter.getActivityId()).isEqualTo("activity");
    assertThat(filter.getStartDate()).isEqualTo("startDate");
    assertThat(filter.getEndDate()).isEqualTo("endDate");
    assertThat(filter.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(filter.getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(filter.getVariable()).isNotNull();
    assertThat(filter.getVariable().getName()).isEqualTo("varName");
    assertThat(filter.getVariable().getValues()).containsExactlyInAnyOrder("val1");
    assertThat(filter.getBatchOperationId()).isEqualTo("batchOperationId");
    assertThat(filter.getParentProcessInstanceKey()).isEqualTo(1234L);
    assertThat(filter.getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldSearchProcessInstanceWithFullSorting() {
    // when
    client
        .newProcessInstanceQuery()
        .sort(
            s ->
                s.key()
                    .asc()
                    .processName()
                    .asc()
                    .processVersion()
                    .asc()
                    .bpmnProcessId()
                    .asc()
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
                    .hasActiveOperation()
                    .desc()
                    .processDefinitionKey()
                    .desc()
                    .tenantId()
                    .asc()
                    .rootInstanceId()
                    .asc())
        .send()
        .join();

    // then
    final ProcessInstanceSearchQueryRequest request =
        gatewayService.getLastRequest(ProcessInstanceSearchQueryRequest.class);
    final List<SearchQuerySortRequest> sorts = request.getSort();
    assertThat(sorts).hasSize(14);
    assertSort(sorts.get(0), "key", "asc");
    assertSort(sorts.get(1), "processName", "asc");
    assertSort(sorts.get(2), "processVersion", "asc");
    assertSort(sorts.get(3), "bpmnProcessId", "asc");
    assertSort(sorts.get(4), "parentProcessInstanceKey", "asc");
    assertSort(sorts.get(5), "parentFlowNodeInstanceKey", "asc");
    assertSort(sorts.get(6), "startDate", "asc");
    assertSort(sorts.get(7), "endDate", "asc");
    assertSort(sorts.get(8), "state", "asc");
    assertSort(sorts.get(9), "incident", "desc");
    assertSort(sorts.get(10), "hasActiveOperation", "desc");
    assertSort(sorts.get(11), "processDefinitionKey", "desc");
    assertSort(sorts.get(12), "tenantId", "asc");
    assertSort(sorts.get(13), "rootInstanceId", "asc");
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
      final SearchQuerySortRequest sort, final String name, final String order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
