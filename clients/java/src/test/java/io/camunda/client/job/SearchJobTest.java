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
package io.camunda.client.job;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.JobFilter;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobKindFilterProperty;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeFilterProperty;
import io.camunda.client.protocol.rest.JobSearchQuery;
import io.camunda.client.protocol.rest.JobStateEnum;
import io.camunda.client.protocol.rest.JobStateFilterProperty;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class SearchJobTest extends ClientRestTest {

  @Test
  void shouldSearchJobs() {
    // when
    client.newJobSearchRequest().send().join();

    // then
    assertThat(RestGatewayService.getLastRequest()).isNotNull();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(RestGatewayService.getLastRequest().getUrl()).isEqualTo("/v2/jobs/search");
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchJobWithFullFilters() {
    // given
    final OffsetDateTime deadlineDate = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endTimeDate = OffsetDateTime.now().plusDays(1);
    // when
    client
        .newJobSearchRequest()
        .filter(
            f ->
                f.jobKey(123L)
                    .type(f2 -> f2.eq("type1"))
                    .worker(f3 -> f3.neq("worker1"))
                    .state(f4 -> f4.in(JobState.COMPLETED, JobState.FAILED))
                    .kind(JobKind.BPMN_ELEMENT)
                    .listenerEventType(ListenerEventType.ASSIGNING)
                    .processDefinitionId("processDefinitionId")
                    .processDefinitionKey(200L)
                    .processInstanceKey(300L)
                    .elementId(f5 -> f5.exists(true))
                    .elementInstanceKey(400L)
                    .tenantId(f6 -> f6.like("tenantId"))
                    .deadline(f7 -> f7.eq(deadlineDate))
                    .deniedReason(f8 -> f8.eq("deniedReason"))
                    .endTime(f9 -> f9.eq(endTimeDate))
                    .errorCode(f10 -> f10.eq("errorCode"))
                    .errorMessage(f11 -> f11.eq("errorMessage"))
                    .hasFailedWithRetriesLeft(true)
                    .isDenied(false)
                    .retries(f12 -> f12.eq(3)))
        .send()
        .join();

    // then

    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getJobKey()).isNotNull();
    assertThat(request.getFilter().getJobKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getType()).isNotNull();
    assertThat(request.getFilter().getType().get$Eq()).isEqualTo("type1");
    assertThat(request.getFilter().getWorker()).isNotNull();
    assertThat(request.getFilter().getWorker().get$Neq()).isEqualTo("worker1");
    assertThat(request.getFilter().getState()).isNotNull();
    assertThat(request.getFilter().getState().get$In())
        .isEqualTo(Arrays.asList(JobStateEnum.COMPLETED, JobStateEnum.FAILED));
    assertThat(request.getFilter().getKind()).isNotNull();
    assertThat(request.getFilter().getKind().get$Eq()).isEqualTo(JobKindEnum.BPMN_ELEMENT);
    assertThat(request.getFilter().getListenerEventType()).isNotNull();
    assertThat(request.getFilter().getListenerEventType().get$Eq())
        .isEqualTo(JobListenerEventTypeEnum.ASSIGNING);
    assertThat(request.getFilter().getProcessDefinitionId()).isNotNull();
    assertThat(request.getFilter().getProcessDefinitionId().get$Eq())
        .isEqualTo("processDefinitionId");
    assertThat(request.getFilter().getProcessDefinitionKey()).isNotNull();
    assertThat(request.getFilter().getProcessDefinitionKey().get$Eq()).isEqualTo("200");
    assertThat(request.getFilter().getProcessInstanceKey()).isNotNull();
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("300");
    assertThat(request.getFilter().getElementId()).isNotNull();
    assertThat(request.getFilter().getElementId().get$Exists()).isEqualTo(true);
    assertThat(request.getFilter().getElementInstanceKey()).isNotNull();
    assertThat(request.getFilter().getElementInstanceKey().get$Eq()).isEqualTo("400");
    assertThat(request.getFilter().getTenantId()).isNotNull();
    assertThat(request.getFilter().getTenantId().get$Like()).isEqualTo("tenantId");
    assertThat(request.getFilter().getDeadline()).isNotNull();
    assertThat(request.getFilter().getDeadline().get$Eq()).isEqualTo(deadlineDate.toString());
    assertThat(request.getFilter().getDeniedReason()).isNotNull();
    assertThat(request.getFilter().getDeniedReason().get$Eq()).isEqualTo("deniedReason");
    assertThat(request.getFilter().getEndTime()).isNotNull();
    assertThat(request.getFilter().getEndTime().get$Eq()).isEqualTo(endTimeDate.toString());
    assertThat(request.getFilter().getErrorCode()).isNotNull();
    assertThat(request.getFilter().getErrorCode().get$Eq()).isEqualTo("errorCode");
    assertThat(request.getFilter().getErrorMessage()).isNotNull();
    assertThat(request.getFilter().getErrorMessage().get$Eq()).isEqualTo("errorMessage");
    assertThat(request.getFilter().getHasFailedWithRetriesLeft()).isNotNull();
    assertThat(request.getFilter().getHasFailedWithRetriesLeft()).isEqualTo(true);
    assertThat(request.getFilter().getIsDenied()).isNotNull();
    assertThat(request.getFilter().getIsDenied()).isEqualTo(false);
    assertThat(request.getFilter().getRetries()).isNotNull();
    assertThat(request.getFilter().getRetries().get$Eq()).isEqualTo(3);
  }

  @ParameterizedTest
  @EnumSource(
      value = JobState.class,
      names = {"UNKNOWN_ENUM_VALUE"},
      mode = Mode.EXCLUDE)
  void shouldSearchJobWithState(final JobState state) {
    // when
    client.newJobSearchRequest().filter(filter -> filter.state(f -> f.eq(state))).send().join();

    // then
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter())
        .extracting(JobFilter::getState)
        .extracting(JobStateFilterProperty::get$Eq)
        .isEqualTo(JobStateEnum.valueOf(state.name()));
  }

  @ParameterizedTest
  @EnumSource(
      value = JobKind.class,
      names = {"UNKNOWN_ENUM_VALUE"},
      mode = Mode.EXCLUDE)
  void shouldSearchJobWithKind(final JobKind jobKind) {
    // when
    client.newJobSearchRequest().filter(filter -> filter.kind(f -> f.eq(jobKind))).send().join();

    // then
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter())
        .extracting(JobFilter::getKind)
        .extracting(JobKindFilterProperty::get$Eq)
        .isEqualTo(JobKindEnum.valueOf(jobKind.name()));
  }

  @ParameterizedTest
  @EnumSource(
      value = ListenerEventType.class,
      names = {"UNKNOWN_ENUM_VALUE"},
      mode = Mode.EXCLUDE)
  void shouldSearchJobWithListenerEventType(final ListenerEventType listenerEventType) {
    // when
    client
        .newJobSearchRequest()
        .filter(filter -> filter.listenerEventType(f -> f.eq(listenerEventType)))
        .send()
        .join();

    // then
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter())
        .extracting(JobFilter::getListenerEventType)
        .extracting(JobListenerEventTypeFilterProperty::get$Eq)
        .isEqualTo(JobListenerEventTypeEnum.valueOf(listenerEventType.name()));
  }

  @Test
  void shouldSearchJobWithFullSorting() {
    // when
    client
        .newJobSearchRequest()
        .sort(
            s ->
                s.jobKey()
                    .asc()
                    .type()
                    .desc()
                    .worker()
                    .asc()
                    .state()
                    .desc()
                    .kind()
                    .asc()
                    .listenerEventType()
                    .desc()
                    .processDefinitionId()
                    .asc()
                    .processDefinitionKey()
                    .desc()
                    .processInstanceKey()
                    .asc()
                    .elementId()
                    .desc()
                    .elementInstanceKey()
                    .asc()
                    .tenantId()
                    .desc()
                    .deadline()
                    .asc()
                    .deniedReason()
                    .desc()
                    .endTime()
                    .asc()
                    .errorCode()
                    .desc()
                    .errorMessage()
                    .asc()
                    .hasFailedWithRetriesLeft()
                    .desc()
                    .isDenied()
                    .asc()
                    .retries()
                    .desc())
        .send()
        .join();

    // then
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromJobSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(20);
    assertSort(sorts.get(0), "jobKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "type", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "worker", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "state", SortOrderEnum.DESC);
    assertSort(sorts.get(4), "kind", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "listenerEventType", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "processDefinitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "processDefinitionKey", SortOrderEnum.DESC);
    assertSort(sorts.get(8), "processInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "elementId", SortOrderEnum.DESC);
    assertSort(sorts.get(10), "elementInstanceKey", SortOrderEnum.ASC);
    assertSort(sorts.get(11), "tenantId", SortOrderEnum.DESC);
    assertSort(sorts.get(12), "deadline", SortOrderEnum.ASC);
    assertSort(sorts.get(13), "deniedReason", SortOrderEnum.DESC);
    assertSort(sorts.get(14), "endTime", SortOrderEnum.ASC);
    assertSort(sorts.get(15), "errorCode", SortOrderEnum.DESC);
    assertSort(sorts.get(16), "errorMessage", SortOrderEnum.ASC);
    assertSort(sorts.get(17), "hasFailedWithRetriesLeft", SortOrderEnum.DESC);
    assertSort(sorts.get(18), "isDenied", SortOrderEnum.ASC);
    assertSort(sorts.get(19), "retries", SortOrderEnum.DESC);
  }

  @Test
  void shouldSearchJobWithFullPagination() {
    // when
    client.newJobSearchRequest().page(p -> p.from(3).limit(5).before("b").after("a")).send().join();

    // then
    final JobSearchQuery request = gatewayService.getLastRequest(JobSearchQuery.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(3);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getBefore()).isEqualTo("b");
    assertThat(pageRequest.getAfter()).isEqualTo("a");
  }
}
