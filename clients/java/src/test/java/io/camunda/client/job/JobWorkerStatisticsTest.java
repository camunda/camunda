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
import io.camunda.client.api.statistics.response.JobWorkerStatistics;
import io.camunda.client.protocol.rest.JobWorkerStatisticsItem;
import io.camunda.client.protocol.rest.JobWorkerStatisticsQuery;
import io.camunda.client.protocol.rest.JobWorkerStatisticsQueryResult;
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

public class JobWorkerStatisticsTest extends ClientRestTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime FROM = NOW.minusDays(1);
  private static final OffsetDateTime TO = NOW.plusDays(1);
  private static final String JOB_TYPE = "fetch-customer-data";

  @Test
  void shouldGetJobWorkerStatisticsWithEmptyQuery() {
    // given
    final JobWorkerStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobWorkerStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldGetJobWorkerStatistics() {
    // given
    final JobWorkerStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    final JobWorkerStatistics result =
        client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobWorkerStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(2);

    // Verify first worker
    assertThat(result.items().get(0).getWorker()).isEqualTo("worker-1");
    assertThat(result.items().get(0).getCreated().getCount()).isEqualTo(100L);
    assertThat(result.items().get(0).getCompleted().getCount()).isEqualTo(80L);
    assertThat(result.items().get(0).getFailed().getCount()).isEqualTo(5L);

    // Verify second worker
    assertThat(result.items().get(1).getWorker()).isEqualTo("worker-2");
    assertThat(result.items().get(1).getCreated().getCount()).isEqualTo(50L);
    assertThat(result.items().get(1).getCompleted().getCount()).isEqualTo(45L);
    assertThat(result.items().get(1).getFailed().getCount()).isEqualTo(2L);
  }

  @Test
  void shouldGetJobWorkerStatisticsWithPagination() {
    // given
    final JobWorkerStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    client
        .newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE)
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
  void shouldSendCorrectRequestBodyWithTimeRangeAndJobType() {
    // given
    final JobWorkerStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String body = request.getBodyAsString();
    assertThat(body)
        .contains("\"from\":\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(FROM) + "\"");
    assertThat(body)
        .contains("\"to\":\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(TO) + "\"");
    assertThat(body).contains("\"jobType\":\"" + JOB_TYPE + "\"");
  }

  @Test
  void shouldIncludeJobTypeInFilter() {
    // given
    final JobWorkerStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    client.newJobWorkerStatisticsRequest(FROM, TO, "process-payment").send().join();

    // then
    final JobWorkerStatisticsQuery request =
        gatewayService.getLastRequest(JobWorkerStatisticsQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getJobType()).isEqualTo("process-payment");
  }

  @Test
  void shouldIncludePageInRequestBody() {
    // given
    final JobWorkerStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).page(p -> p.limit(5)).send().join();

    // then
    final LoggedRequest lastRequest = RestGatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"page\"");
    assertThat(requestBody).contains("\"limit\":5");
  }

  @Test
  void shouldHandleEmptyResults() {
    // given
    final JobWorkerStatisticsQueryResult response = createEmptyResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    final JobWorkerStatistics result =
        client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldHandleNullMetrics() {
    // given
    final JobWorkerStatisticsQueryResult response = createResponseWithNullMetrics();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    final JobWorkerStatistics result =
        client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).getCreated().getCount()).isZero();
    assertThat(result.items().get(0).getCompleted().getCount()).isZero();
    assertThat(result.items().get(0).getFailed().getCount()).isZero();
    assertThat(result.items().get(0).getCreated().getLastUpdatedAt()).isNull();
  }

  @Test
  void shouldReturnPaginatedResponse() {
    // given
    final JobWorkerStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    final JobWorkerStatistics result =
        client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.page().totalItems()).isEqualTo(3L);
    assertThat(result.page().endCursor()).isEqualTo("cursor123");
  }

  @Test
  void shouldVerifyResultsAreOrderedByWorker() {
    // given
    final JobWorkerStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobWorkerStatisticsRequest(response);

    // when
    final JobWorkerStatistics result =
        client.newJobWorkerStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then - results are ordered as returned by the API (worker ASC from SQL)
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).getWorker()).isEqualTo("worker-1");
    assertThat(result.items().get(1).getWorker()).isEqualTo("worker-2");
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private JobWorkerStatisticsQueryResult createTestResponse() {
    final JobWorkerStatisticsQueryResult response = new JobWorkerStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(
        Arrays.asList(
            createWorkerItem("worker-1", 100L, 80L, 5L),
            createWorkerItem("worker-2", 50L, 45L, 2L)));
    return response;
  }

  private JobWorkerStatisticsQueryResult createPaginatedResponse() {
    final JobWorkerStatisticsQueryResult response = new JobWorkerStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse().totalItems(3L).endCursor("cursor123"));
    response.setItems(
        Arrays.asList(
            createWorkerItem("worker-1", 100L, 80L, 5L),
            createWorkerItem("worker-2", 50L, 45L, 2L),
            createWorkerItem("worker-3", 30L, 25L, 1L)));
    return response;
  }

  private JobWorkerStatisticsQueryResult createEmptyResponse() {
    final JobWorkerStatisticsQueryResult response = new JobWorkerStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(Collections.emptyList());
    return response;
  }

  private JobWorkerStatisticsQueryResult createResponseWithNullMetrics() {
    final JobWorkerStatisticsQueryResult response = new JobWorkerStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    final JobWorkerStatisticsItem item = new JobWorkerStatisticsItem();
    item.setWorker("worker-1");
    response.setItems(Collections.singletonList(item));
    return response;
  }

  private JobWorkerStatisticsItem createWorkerItem(
      final String worker,
      final long createdCount,
      final long completedCount,
      final long failedCount) {
    final JobWorkerStatisticsItem item = new JobWorkerStatisticsItem();
    item.setWorker(worker);
    item.setCreated(createStatusMetric(createdCount));
    item.setCompleted(createStatusMetric(completedCount));
    item.setFailed(createStatusMetric(failedCount));
    return item;
  }

  private StatusMetric createStatusMetric(final long count) {
    final StatusMetric metric = new StatusMetric();
    metric.setCount(count);
    metric.setLastUpdatedAt(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
    return metric;
  }
}
