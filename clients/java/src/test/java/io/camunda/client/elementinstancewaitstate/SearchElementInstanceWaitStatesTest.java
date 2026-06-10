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
package io.camunda.client.elementinstancewaitstate;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.JobWaitStateDetails;
import io.camunda.client.api.search.response.MessageWaitStateDetails;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.ElementInstanceWaitStateFilter;
import io.camunda.client.protocol.rest.ElementInstanceWaitStateQuery;
import io.camunda.client.protocol.rest.ElementInstanceWaitStateQueryResult;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.protocol.rest.WaitStateElementTypeEnum;
import io.camunda.client.protocol.rest.WaitStateTypeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class SearchElementInstanceWaitStatesTest extends ClientRestTest {

  @Test
  public void shouldSearchWithEmptyQuery() {
    // given
    final ElementInstanceWaitStateQueryResult response = buildEmptyResponse();
    gatewayService.onSearchElementInstanceWaitStatesRequest(response);

    // when
    client.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getElementInstanceWaitStateSearchUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchWithKeyFilters() {
    // given
    gatewayService.onSearchElementInstanceWaitStatesRequest(buildEmptyResponse());

    // when
    client
        .newElementInstanceWaitStateSearchRequest()
        .filter(
            f ->
                f.processInstanceKey(100L)
                    .rootProcessInstanceKey(200L)
                    .elementInstanceKey(300L)
                    .elementId("task-1"))
        .send()
        .join();

    // then
    final ElementInstanceWaitStateQuery request =
        gatewayService.getLastRequest(ElementInstanceWaitStateQuery.class);
    final ElementInstanceWaitStateFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getProcessInstanceKey().get$Eq()).isEqualTo("100");
    assertThat(filter.getRootProcessInstanceKey().get$Eq()).isEqualTo("200");
    assertThat(filter.getElementInstanceKey().get$Eq()).isEqualTo("300");
    assertThat(filter.getElementId().get$Eq()).isEqualTo("task-1");
  }

  @Test
  public void shouldSearchWithEnumFilters() {
    // given
    gatewayService.onSearchElementInstanceWaitStatesRequest(buildEmptyResponse());

    // when
    client
        .newElementInstanceWaitStateSearchRequest()
        .filter(
            f -> f.elementType(WaitStateElementType.SERVICE_TASK).waitStateType(WaitStateType.JOB))
        .send()
        .join();

    // then
    final ElementInstanceWaitStateQuery request =
        gatewayService.getLastRequest(ElementInstanceWaitStateQuery.class);
    final ElementInstanceWaitStateFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getElementType().get$Eq()).isEqualTo(WaitStateElementTypeEnum.SERVICE_TASK);
    assertThat(filter.getWaitStateType().get$Eq()).isEqualTo(WaitStateTypeEnum.JOB);
  }

  @Test
  void shouldSearchWithAllSortFields() {
    // given
    gatewayService.onSearchElementInstanceWaitStatesRequest(buildEmptyResponse());

    // when
    client
        .newElementInstanceWaitStateSearchRequest()
        .sort(
            s ->
                s.elementInstanceKey()
                    .asc()
                    .processInstanceKey()
                    .desc()
                    .rootProcessInstanceKey()
                    .asc()
                    .elementId()
                    .desc())
        .send()
        .join();

    // then
    final ElementInstanceWaitStateQuery request =
        gatewayService.getLastRequest(ElementInstanceWaitStateQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromElementInstanceWaitStateSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(4);
    assertSort(sorts.get(0), "elementInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processInstanceKey", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "rootProcessInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "elementId", SortOrderEnum.DESC);
  }

  @Test
  public void shouldSearchWithPage() {
    // given
    gatewayService.onSearchElementInstanceWaitStatesRequest(buildEmptyResponse());

    // when
    client.newElementInstanceWaitStateSearchRequest().page(p -> p.limit(5)).send().join();

    // then
    final ElementInstanceWaitStateQuery request =
        gatewayService.getLastRequest(ElementInstanceWaitStateQuery.class);
    assertThat(request.getPage()).isNotNull();
    assertThat(request.getPage().getLimit()).isEqualTo(5);
  }

  @Test
  public void shouldMapJobResponseFields() {
    // given
    final io.camunda.client.protocol.rest.JobWaitStateDetails jobDetails =
        new io.camunda.client.protocol.rest.JobWaitStateDetails();
    jobDetails.setWaitStateType("JOB");
    jobDetails.setJobKey("999");
    jobDetails.setJobType("payment");
    jobDetails.setJobKind(JobKindEnum.BPMN_ELEMENT);
    jobDetails.setListenerEventType(JobListenerEventTypeEnum.UNSPECIFIED);
    jobDetails.setRetries(3);

    final io.camunda.client.protocol.rest.ElementInstanceWaitStateResult item =
        new io.camunda.client.protocol.rest.ElementInstanceWaitStateResult();
    item.setProcessInstanceKey("200");
    item.setRootProcessInstanceKey("100");
    item.setElementInstanceKey("300");
    item.setElementId("task-1");
    item.setTenantId("<default>");
    item.setBpmnProcessId("payment-process");
    item.setDetails(jobDetails);

    final ElementInstanceWaitStateQueryResult response = buildEmptyResponse();
    response.addItemsItem(item);
    gatewayService.onSearchElementInstanceWaitStatesRequest(response);

    // when
    final SearchResponse<io.camunda.client.api.search.response.ElementInstanceWaitStateResult>
        result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(1);
    final io.camunda.client.api.search.response.ElementInstanceWaitStateResult mapped =
        result.items().get(0);
    assertThat(mapped.getProcessInstanceKey()).isEqualTo("200");
    assertThat(mapped.getRootProcessInstanceKey()).isEqualTo("100");
    assertThat(mapped.getElementInstanceKey()).isEqualTo("300");
    assertThat(mapped.getElementId()).isEqualTo("task-1");
    assertThat(mapped.getTenantId()).isEqualTo("<default>");
    assertThat(mapped.getBpmnProcessId()).isEqualTo("payment-process");
    assertThat(mapped.getDetails()).isInstanceOf(JobWaitStateDetails.class);
    final JobWaitStateDetails details = (JobWaitStateDetails) mapped.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(details.getJobKey()).isEqualTo("999");
    assertThat(details.getJobType()).isEqualTo("payment");
    assertThat(details.getJobKind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(details.getListenerEventType()).isEqualTo(ListenerEventType.UNSPECIFIED);
    assertThat(details.getRetries()).isEqualTo(3);
  }

  @Test
  public void shouldMapMessageResponseFields() {
    // given
    final io.camunda.client.protocol.rest.MessageWaitStateDetails messageDetails =
        new io.camunda.client.protocol.rest.MessageWaitStateDetails();
    messageDetails.setWaitStateType("MESSAGE");
    messageDetails.setMessageName("order-received");
    messageDetails.setCorrelationKey("order-42");

    final io.camunda.client.protocol.rest.ElementInstanceWaitStateResult item =
        new io.camunda.client.protocol.rest.ElementInstanceWaitStateResult();
    item.setProcessInstanceKey("200");
    item.setElementInstanceKey("300");
    item.setElementId("receive-1");
    item.setTenantId("<default>");
    item.setDetails(messageDetails);

    final ElementInstanceWaitStateQueryResult response = buildEmptyResponse();
    response.addItemsItem(item);
    gatewayService.onSearchElementInstanceWaitStatesRequest(response);

    // when
    final SearchResponse<io.camunda.client.api.search.response.ElementInstanceWaitStateResult>
        result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(1);
    final io.camunda.client.api.search.response.ElementInstanceWaitStateResult mapped =
        result.items().get(0);
    assertThat(mapped.getDetails()).isInstanceOf(MessageWaitStateDetails.class);
    final MessageWaitStateDetails details = (MessageWaitStateDetails) mapped.getDetails();
    assertThat(details.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE);
    assertThat(details.getMessageName()).isEqualTo("order-received");
    assertThat(details.getCorrelationKey()).isEqualTo("order-42");
  }

  private static ElementInstanceWaitStateQueryResult buildEmptyResponse() {
    final ElementInstanceWaitStateQueryResult response = new ElementInstanceWaitStateQueryResult();
    final SearchQueryPageResponse page = new SearchQueryPageResponse();
    page.setTotalItems(0L);
    response.setPage(page);
    response.setItems(new ArrayList<>());
    return response;
  }
}
