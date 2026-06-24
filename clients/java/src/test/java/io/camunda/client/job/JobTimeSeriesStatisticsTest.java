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
import io.camunda.client.api.statistics.response.JobTimeSeriesStatistics;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsItem;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsQuery;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.StatusMetric;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class JobTimeSeriesStatisticsTest extends ClientRestTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime FROM = NOW.minusDays(1);
  private static final OffsetDateTime TO = NOW.plusDays(1);
  private static final String JOB_TYPE = "fetch-customer-data";

  @Test
  void shouldGetJobTimeSeriesStatisticsWithEmptyQuery() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobTimeSeriesStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldGetJobTimeSeriesStatistics() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    final JobTimeSeriesStatistics result =
        client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobTimeSeriesStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(2);

    // Verify first bucket
    assertThat(result.items().get(0).getTime()).isNotNull();
    assertThat(result.items().get(0).getCreated().getCount()).isEqualTo(100L);
    assertThat(result.items().get(0).getCompleted().getCount()).isEqualTo(80L);
    assertThat(result.items().get(0).getFailed().getCount()).isEqualTo(5L);

    // Verify second bucket
    assertThat(result.items().get(1).getTime()).isNotNull();
    assertThat(result.items().get(1).getCreated().getCount()).isEqualTo(50L);
    assertThat(result.items().get(1).getCompleted().getCount()).isEqualTo(45L);
    assertThat(result.items().get(1).getFailed().getCount()).isEqualTo(2L);
  }

  @Test
  void shouldSendCorrectRequestBodyWithTimeRangeAndJobType() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

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
  void shouldIncludeResolutionInRequestBody() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client
        .newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE)
        .resolution(Duration.ofMinutes(5))
        .send()
        .join();

    // then
    final JobTimeSeriesStatisticsQuery request =
        gatewayService.getLastRequest(JobTimeSeriesStatisticsQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getResolution()).isEqualTo("PT5M");
  }

  @Test
  void shouldOmitResolutionWhenNotSet() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final JobTimeSeriesStatisticsQuery request =
        gatewayService.getLastRequest(JobTimeSeriesStatisticsQuery.class);
    assertThat(request.getFilter().getResolution()).isNull();
  }

  @Test
  void shouldGetJobTimeSeriesStatisticsWithPagination() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client
        .newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE)
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
  void shouldHandleEmptyResults() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createEmptyResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    final JobTimeSeriesStatistics result =
        client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldHandleNullMetrics() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createResponseWithNullMetrics();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    final JobTimeSeriesStatistics result =
        client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

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
    final JobTimeSeriesStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    final JobTimeSeriesStatistics result =
        client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.page().totalItems()).isEqualTo(3L);
    assertThat(result.page().endCursor()).isEqualTo("cursor123");
  }

  @Test
  void shouldIncludePageInRequestBody() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createPaginatedResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    client
        .newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE)
        .page(p -> p.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = RestGatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();
    assertThat(requestBody).contains("\"page\"");
    assertThat(requestBody).contains("\"limit\":5");
  }

  @Test
  void shouldVerifyBucketsAreOrderedAscendingByTime() {
    // given
    final JobTimeSeriesStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobTimeSeriesStatisticsRequest(response);

    // when
    final JobTimeSeriesStatistics result =
        client.newJobTimeSeriesStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then — items returned in the order provided by the server (ascending by time)
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).getTime()).isBefore(result.items().get(1).getTime());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private JobTimeSeriesStatisticsQueryResult createTestResponse() {
    final JobTimeSeriesStatisticsQueryResult response = new JobTimeSeriesStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(
        Arrays.asList(
            createBucketItem(NOW.minusHours(2), 100L, 80L, 5L),
            createBucketItem(NOW.minusHours(1), 50L, 45L, 2L)));
    return response;
  }

  private JobTimeSeriesStatisticsQueryResult createPaginatedResponse() {
    final JobTimeSeriesStatisticsQueryResult response = new JobTimeSeriesStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse().totalItems(3L).endCursor("cursor123"));
    response.setItems(
        Arrays.asList(
            createBucketItem(NOW.minusHours(3), 100L, 80L, 5L),
            createBucketItem(NOW.minusHours(2), 50L, 45L, 2L),
            createBucketItem(NOW.minusHours(1), 30L, 25L, 1L)));
    return response;
  }

  private JobTimeSeriesStatisticsQueryResult createEmptyResponse() {
    final JobTimeSeriesStatisticsQueryResult response = new JobTimeSeriesStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(Collections.emptyList());
    return response;
  }

  private JobTimeSeriesStatisticsQueryResult createResponseWithNullMetrics() {
    final JobTimeSeriesStatisticsQueryResult response = new JobTimeSeriesStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    final JobTimeSeriesStatisticsItem item = new JobTimeSeriesStatisticsItem();
    item.setTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(NOW.minusHours(1)));
    response.setItems(Collections.singletonList(item));
    return response;
  }

  private JobTimeSeriesStatisticsItem createBucketItem(
      final OffsetDateTime time,
      final long createdCount,
      final long completedCount,
      final long failedCount) {
    final JobTimeSeriesStatisticsItem item = new JobTimeSeriesStatisticsItem();
    item.setTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time));
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
