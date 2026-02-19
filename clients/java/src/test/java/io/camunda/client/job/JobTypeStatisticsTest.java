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

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.statistics.response.JobTypeStatistics;
import io.camunda.client.protocol.rest.JobTypeStatisticsFilter;
import io.camunda.client.protocol.rest.JobTypeStatisticsItem;
import io.camunda.client.protocol.rest.JobTypeStatisticsQuery;
import io.camunda.client.protocol.rest.JobTypeStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.StatusMetric;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class JobTypeStatisticsTest extends ClientRestTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime FROM = NOW.minusDays(1); // now-1day
  private static final OffsetDateTime TO = NOW.plusDays(1); // now+1day

  @Test
  void shouldGetJobTypeStatisticsWithEmptyQuery() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobTypeStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldGetJobTypeStatisticsWithFilter() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client.newJobTypeStatisticsRequest(FROM, TO).filter(f -> f.jobType("myJobType")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/jobs/statistics/by-types");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldGetJobTypeStatisticsWithPagination() {
    // given
    final JobTypeStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .page(p -> p.limit(10).after("cursor123"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    final String body = request.getBodyAsString();
    assertThat(body).contains("\"page\"");
    assertThat(body).contains("\"limit\":10");
    assertThat(body).contains("\"after\":\"cursor123\"");
  }

  @Test
  void shouldGetJobTypeStatistics() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    final JobTypeStatistics result = client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/jobs/statistics/by-types");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(2);

    // Verify first job type
    assertThat(result.items().get(0).getJobType()).isEqualTo("jobTypeA");
    assertThat(result.items().get(0).getCreated().getCount()).isEqualTo(100L);
    assertThat(result.items().get(0).getCompleted().getCount()).isEqualTo(80L);
    assertThat(result.items().get(0).getFailed().getCount()).isEqualTo(5L);
    assertThat(result.items().get(0).getWorkers()).isEqualTo(3);

    // Verify second job type
    assertThat(result.items().get(1).getJobType()).isEqualTo("jobTypeB");
    assertThat(result.items().get(1).getCreated().getCount()).isEqualTo(50L);
    assertThat(result.items().get(1).getCompleted().getCount()).isEqualTo(45L);
    assertThat(result.items().get(1).getFailed().getCount()).isEqualTo(2L);
    assertThat(result.items().get(1).getWorkers()).isEqualTo(2);
  }

  @Test
  void shouldSendCorrectRequestBodyWithTimeRange() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String body = request.getBodyAsString();
    assertThat(body)
        .contains("\"from\":\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(FROM) + "\"");
    assertThat(body)
        .contains("\"to\":\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(TO) + "\"");
  }

  @Test
  void shouldFilterByJobTypeExactMatch() {
    // given
    final JobTypeStatisticsQueryResult response = createSingleJobTypeResponse("myJobType");
    gatewayService.onJobStatisticsRequest(response);

    // when
    client.newJobTypeStatisticsRequest(FROM, TO).filter(f -> f.jobType("myJobType")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    final String body = request.getBodyAsString();
    assertThat(body).contains("\"jobType\"");
    assertThat(body).contains("myJobType");
  }

  @Test
  void shouldFilterByJobTypeLikePattern() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType(jt -> jt.like("fetch-*")))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    final String body = request.getBodyAsString();
    assertThat(body).contains("\"jobType\"");
    assertThat(body).contains("fetch-*");
  }

  @Test
  void shouldFilterByJobTypeInList() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType(jt -> jt.in("jobTypeA", "jobTypeB")))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    final String body = request.getBodyAsString();
    assertThat(body).contains("\"jobType\"");
    assertThat(body).contains("jobTypeA");
    assertThat(body).contains("jobTypeB");
  }

  @Test
  void shouldFilterByJobTypeWithAdvancedOperations() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType(jt -> jt.eq("exactMatch")))
        .send()
        .join();

    // then
    final JobTypeStatisticsQuery request =
        gatewayService.getLastRequest(JobTypeStatisticsQuery.class);
    final JobTypeStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getJobType().get$Eq()).isEqualTo("exactMatch");
  }

  @Test
  void shouldFilterByJobTypeNotEquals() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType(jt -> jt.neq("excludeThis")))
        .send()
        .join();

    // then
    final JobTypeStatisticsQuery request =
        gatewayService.getLastRequest(JobTypeStatisticsQuery.class);
    final JobTypeStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getJobType().get$Neq()).isEqualTo("excludeThis");
  }

  @Test
  void shouldFilterByJobTypeExists() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType(jt -> jt.exists(true)))
        .send()
        .join();

    // then
    final JobTypeStatisticsQuery request =
        gatewayService.getLastRequest(JobTypeStatisticsQuery.class);
    final JobTypeStatisticsFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getJobType().get$Exists()).isTrue();
  }

  @Test
  void shouldIncludeFilterAndPageInRequestBody() {
    // given
    final JobTypeStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    client
        .newJobTypeStatisticsRequest(FROM, TO)
        .filter(f -> f.jobType("testJobType"))
        .page(p -> p.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = RestGatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"filter\"");
    assertThat(requestBody).contains("\"jobType\"");
    assertThat(requestBody).contains("testJobType");
    assertThat(requestBody).contains("\"page\"");
  }

  @Test
  void shouldHandleEmptyResults() {
    // given
    final JobTypeStatisticsQueryResult response = createEmptyResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    final JobTypeStatistics result = client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldHandleNullMetrics() {
    // given
    final JobTypeStatisticsQueryResult response = createResponseWithNullMetrics();
    gatewayService.onJobStatisticsRequest(response);

    // when
    final JobTypeStatistics result = client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).getCreated().getCount()).isZero();
    assertThat(result.items().get(0).getCompleted().getCount()).isZero();
    assertThat(result.items().get(0).getFailed().getCount()).isZero();
    assertThat(result.items().get(0).getCreated().getLastUpdatedAt()).isNull();
  }

  @Test
  void shouldHandleJobTypeWithZeroWorkers() {
    // given
    final JobTypeStatisticsQueryResult response = createResponseWithZeroWorkers();
    gatewayService.onJobStatisticsRequest(response);

    // when
    final JobTypeStatistics result = client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).getWorkers()).isZero();
  }

  @Test
  void shouldVerifyResultsAreSortedByJobType() {
    // given
    final JobTypeStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobStatisticsRequest(response);

    // when
    final JobTypeStatistics result = client.newJobTypeStatisticsRequest(FROM, TO).send().join();

    // then - results should be sorted by jobType (from SQL ORDER BY)
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).getJobType()).isEqualTo("jobTypeA");
    assertThat(result.items().get(1).getJobType()).isEqualTo("jobTypeB");
  }

  private JobTypeStatisticsQueryResult createTestResponse() {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(
        Arrays.asList(
            createJobTypeItem("jobTypeA", 100L, 80L, 5L, 3),
            createJobTypeItem("jobTypeB", 50L, 45L, 2L, 2)));
    return response;
  }

  private JobTypeStatisticsQueryResult createSingleJobTypeResponse(final String jobType) {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(Collections.singletonList(createJobTypeItem(jobType, 100L, 80L, 5L, 3)));
    return response;
  }

  private JobTypeStatisticsQueryResult createPaginatedResponse() {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse().totalItems(3L).endCursor("cursor123"));
    response.setItems(
        Arrays.asList(
            createJobTypeItem("jobTypeA", 100L, 80L, 5L, 3),
            createJobTypeItem("jobTypeB", 50L, 45L, 2L, 2),
            createJobTypeItem("jobTypeC", 30L, 25L, 1L, 1)));
    return response;
  }

  private JobTypeStatisticsQueryResult createEmptyResponse() {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(Collections.emptyList());
    return response;
  }

  private JobTypeStatisticsQueryResult createResponseWithNullMetrics() {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    final JobTypeStatisticsItem item = new JobTypeStatisticsItem();
    item.setJobType("testJobType");
    item.setWorkers(1);
    response.setItems(Collections.singletonList(item));
    return response;
  }

  private JobTypeStatisticsQueryResult createResponseWithZeroWorkers() {
    final JobTypeStatisticsQueryResult response = new JobTypeStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(
        Collections.singletonList(createJobTypeItem("noWorkersType", 10L, 5L, 1L, 0)));
    return response;
  }

  private JobTypeStatisticsItem createJobTypeItem(
      final String jobType,
      final long createdCount,
      final long completedCount,
      final long failedCount,
      final int workers) {
    final JobTypeStatisticsItem item = new JobTypeStatisticsItem();
    item.setJobType(jobType);
    item.setCreated(createStatusMetric(createdCount));
    item.setCompleted(createStatusMetric(completedCount));
    item.setFailed(createStatusMetric(failedCount));
    item.setWorkers(workers);
    return item;
  }

  private StatusMetric createStatusMetric(final long count) {
    final StatusMetric metric = new StatusMetric();
    metric.setCount(count);
    metric.setLastUpdatedAt(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
    return metric;
  }
}
