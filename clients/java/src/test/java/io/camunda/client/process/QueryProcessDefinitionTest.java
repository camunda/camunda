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

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.ProcessDefinitionFilter;
import io.camunda.client.protocol.rest.ProcessDefinitionSearchQuery;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class QueryProcessDefinitionTest extends ClientRestTest {

  @Test
  void shouldGetProcessDefinitionXml() {
    // when
    final long processDefinitionKey = 1L;
    client.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/process-definitions/" + processDefinitionKey + "/xml");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  void shouldGetProcessDefinitionForm() {
    // when
    final long processDefinitionKey = 1L;
    client.newProcessDefinitionGetFormRequest(processDefinitionKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/process-definitions/" + processDefinitionKey + "/form");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  public void shouldGetProcessDefinitionByKey() {
    // when
    client.newProcessDefinitionGetRequest(123L).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/process-definitions/123");
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  public void shouldSearchProcessDefinitionWithEmptyQuery() {
    // when
    client.newProcessDefinitionSearchRequest().send().join();

    // then
    final ProcessDefinitionSearchQuery request =
        gatewayService.getLastRequest(ProcessDefinitionSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchProcessDefinitionWithFullFilters() {
    // when
    client
        .newProcessDefinitionSearchRequest()
        .filter(
            f ->
                f.processDefinitionKey(5L)
                    .name("Order process")
                    .resourceName("usertest/complex-process.bpmn")
                    .version(2)
                    .versionTag("alpha")
                    .processDefinitionId("orderProcess")
                    .tenantId("<default>"))
        .send()
        .join();
    // then
    final ProcessDefinitionSearchQuery request =
        gatewayService.getLastRequest(ProcessDefinitionSearchQuery.class);
    final ProcessDefinitionFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getProcessDefinitionKey()).isEqualTo("5");
    assertThat(filter.getName()).isEqualTo("Order process");
    assertThat(filter.getResourceName()).isEqualTo("usertest/complex-process.bpmn");
    assertThat(filter.getVersion()).isEqualTo(2);
    assertThat(filter.getVersionTag()).isEqualTo("alpha");
    assertThat(filter.getProcessDefinitionId()).isEqualTo("orderProcess");
    assertThat(filter.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldSearchProcessDefinitionWithFullSorting() {
    // when
    client
        .newProcessDefinitionSearchRequest()
        .sort(
            s ->
                s.processDefinitionKey()
                    .asc()
                    .name()
                    .desc()
                    .resourceName()
                    .asc()
                    .version()
                    .asc()
                    .versionTag()
                    .desc()
                    .processDefinitionId()
                    .desc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final ProcessDefinitionSearchQuery request =
        gatewayService.getLastRequest(ProcessDefinitionSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromProcessDefinitionSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(7);
    assertSort(sorts.get(0), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "name", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "resourceName", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "version", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "versionTag", SortOrderEnum.DESC);
    assertSort(sorts.get(5), "processDefinitionId", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "tenantId", SortOrderEnum.ASC);
  }

  @Test
  void shouldSearchWithFullPagination() {
    // when
    client
        .newProcessDefinitionSearchRequest()
        .page(
            p ->
                p.from(23)
                    .limit(5)
                    .searchBefore(Collections.singletonList("b"))
                    .searchAfter(Collections.singletonList("a")))
        .send()
        .join();

    // then
    final ProcessDefinitionSearchQuery request =
        gatewayService.getLastRequest(ProcessDefinitionSearchQuery.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(23);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getSearchBefore()).isEqualTo(Collections.singletonList("b"));
    assertThat(pageRequest.getSearchAfter()).isEqualTo(Collections.singletonList("a"));
  }

  private void assertSort(
      final SearchRequestSort sort, final String name, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }
}
