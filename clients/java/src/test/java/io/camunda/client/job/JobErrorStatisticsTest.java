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
import io.camunda.client.api.statistics.response.JobErrorStatistics;
import io.camunda.client.protocol.rest.JobErrorStatisticsItem;
import io.camunda.client.protocol.rest.JobErrorStatisticsQuery;
import io.camunda.client.protocol.rest.JobErrorStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class JobErrorStatisticsTest extends ClientRestTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
  private static final OffsetDateTime FROM = NOW.minusDays(1);
  private static final OffsetDateTime TO = NOW.plusDays(1);
  private static final String JOB_TYPE = "fetch-customer-data";

  @Test
  void shouldGetJobErrorStatisticsWithEmptyQuery() {
    // given
    final JobErrorStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobErrorStatisticsRequest(response);

    // when
    client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getJobErrorStatisticsUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldGetJobErrorStatistics() {
    // given
    final JobErrorStatisticsQueryResult response = createTestResponse();
    gatewayService.onJobErrorStatisticsRequest(response);

    // when
    final JobErrorStatistics result =
        client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(2);

    assertThat(result.items().get(0).getErrorCode()).isEqualTo("IO_ERROR");
    assertThat(result.items().get(0).getErrorMessage()).isEqualTo("Disk full");
    assertThat(result.items().get(0).getWorkers()).isEqualTo(3);

    assertThat(result.items().get(1).getErrorCode()).isEqualTo("TIMEOUT");
    assertThat(result.items().get(1).getErrorMessage()).isEqualTo("Connection timed out");
    assertThat(result.items().get(1).getWorkers()).isEqualTo(7);
  }

  @Test
  void shouldSendCorrectRequestBodyWithTimeRangeAndJobType() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createTestResponse());

    // when
    client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

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
  void shouldGetJobErrorStatisticsWithPagination() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createPaginatedResponse());

    // when
    client
        .newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE)
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
    gatewayService.onJobErrorStatisticsRequest(createEmptyResponse());

    // when
    final JobErrorStatistics result =
        client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnPaginatedResponse() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createPaginatedResponse());

    // when
    final JobErrorStatistics result =
        client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.page().totalItems()).isEqualTo(3L);
    assertThat(result.page().endCursor()).isEqualTo("cursor123");
  }

  @Test
  void shouldVerifyRequestBodyContainsFilterFields() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createTestResponse());

    // when
    client.newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE).send().join();

    // then
    final JobErrorStatisticsQuery request =
        gatewayService.getLastRequest(JobErrorStatisticsQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getJobType()).isEqualTo(JOB_TYPE);
    assertThat(request.getFilter().getFrom())
        .isEqualTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(FROM));
    assertThat(request.getFilter().getTo())
        .isEqualTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(TO));
  }

  @Test
  void shouldApplyErrorCodeFilter() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createTestResponse());

    // when
    client
        .newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE)
        .filter(f -> f.errorCode("IO_ERROR"))
        .send()
        .join();

    // then
    final JobErrorStatisticsQuery request =
        gatewayService.getLastRequest(JobErrorStatisticsQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getErrorCode()).isNotNull();
    assertThat(request.getFilter().getErrorCode().get$Eq()).isEqualTo("IO_ERROR");
    // Constructor-set fields must be preserved
    assertThat(request.getFilter().getJobType()).isEqualTo(JOB_TYPE);
    assertThat(request.getFilter().getFrom())
        .isEqualTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(FROM));
    assertThat(request.getFilter().getTo())
        .isEqualTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(TO));
  }

  @Test
  void shouldApplyErrorCodeLikeFilter() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createTestResponse());

    // when
    client
        .newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE)
        .filter(f -> f.errorCode(c -> c.like("UNHANDLED_*")))
        .send()
        .join();

    // then
    final JobErrorStatisticsQuery request =
        gatewayService.getLastRequest(JobErrorStatisticsQuery.class);
    assertThat(request.getFilter().getErrorCode()).isNotNull();
    assertThat(request.getFilter().getErrorCode().get$Like()).isEqualTo("UNHANDLED_*");
  }

  @Test
  void shouldApplyErrorMessageFilter() {
    // given
    gatewayService.onJobErrorStatisticsRequest(createTestResponse());

    // when
    client
        .newJobErrorStatisticsRequest(FROM, TO, JOB_TYPE)
        .filter(f -> f.errorMessage(m -> m.like("unexpected*")))
        .send()
        .join();

    // then
    final JobErrorStatisticsQuery request =
        gatewayService.getLastRequest(JobErrorStatisticsQuery.class);
    assertThat(request.getFilter().getErrorMessage()).isNotNull();
    assertThat(request.getFilter().getErrorMessage().get$Like()).isEqualTo("unexpected*");
    // Constructor-set fields must be preserved
    assertThat(request.getFilter().getJobType()).isEqualTo(JOB_TYPE);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private JobErrorStatisticsQueryResult createTestResponse() {
    final JobErrorStatisticsQueryResult response = new JobErrorStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(
        Arrays.asList(
            createErrorItem("IO_ERROR", "Disk full", 3),
            createErrorItem("TIMEOUT", "Connection timed out", 7)));
    return response;
  }

  private JobErrorStatisticsQueryResult createPaginatedResponse() {
    final JobErrorStatisticsQueryResult response = new JobErrorStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse().totalItems(3L).endCursor("cursor123"));
    response.setItems(
        Arrays.asList(
            createErrorItem("ERR_A", "msg-a", 1),
            createErrorItem("ERR_B", "msg-b", 2),
            createErrorItem("ERR_C", "msg-c", 3)));
    return response;
  }

  private JobErrorStatisticsQueryResult createEmptyResponse() {
    final JobErrorStatisticsQueryResult response = new JobErrorStatisticsQueryResult();
    response.setPage(new SearchQueryPageResponse());
    response.setItems(Collections.emptyList());
    return response;
  }

  private JobErrorStatisticsItem createErrorItem(
      final String errorCode, final String errorMessage, final int workers) {
    final JobErrorStatisticsItem item = new JobErrorStatisticsItem();
    item.setErrorCode(errorCode);
    item.setErrorMessage(errorMessage);
    item.setWorkers(workers);
    return item;
  }
}
