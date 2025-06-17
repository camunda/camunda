package io.camunda.client.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.JobSearchQueryRequest;
import io.camunda.client.protocol.rest.JobStateEnum;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class SearchJobTest extends ClientRestTest {

  @Test
  void shouldSearchJobs() {
    // when
    client.newJobSearchRequest().send().join();

    // then
    assertThat(RestGatewayService.getLastRequest()).isNotNull();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(RestGatewayService.getLastRequest().getUrl()).isEqualTo("/v2/job/search");
    final JobSearchQueryRequest request =
        gatewayService.getLastRequest(JobSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchJobWithFullFilters() {
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
                    .tenantId(f6 -> f6.like("tenantId")))
        .send()
        .join();

    // then

    final JobSearchQueryRequest request =
        gatewayService.getLastRequest(JobSearchQueryRequest.class);
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
                    .desc())
        .send()
        .join();

    // then
    final JobSearchQueryRequest request =
        gatewayService.getLastRequest(JobSearchQueryRequest.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromJobSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(12);
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
  }

  private void assertSort(
      final SearchRequestSort sort, final String name, final SortOrderEnum order) {
    assertThat(sort.getField()).isEqualTo(name);
    assertThat(sort.getOrder()).isEqualTo(order);
  }

  @Test
  void shouldSearchJobWithFullPagination() {
    // when
    client
        .newJobSearchRequest()
        .page(
            p ->
                p.from(3)
                    .limit(5)
                    .searchBefore(Collections.singletonList("b"))
                    .searchAfter(Collections.singletonList("a")))
        .send()
        .join();

    // then
    final JobSearchQueryRequest request =
        gatewayService.getLastRequest(JobSearchQueryRequest.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(3);
    assertThat(pageRequest.getLimit()).isEqualTo(5);
    assertThat(pageRequest.getSearchBefore()).isNotNull();
    assertThat(pageRequest.getSearchBefore()).containsExactly("b");
    assertThat(pageRequest.getSearchAfter()).isNotNull();
    assertThat(pageRequest.getSearchAfter()).containsExactly("a");
  }
}
