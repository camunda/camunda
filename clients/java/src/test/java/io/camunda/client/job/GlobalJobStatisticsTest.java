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
import io.camunda.client.api.statistics.response.GlobalJobStatistics;
import io.camunda.client.protocol.rest.GlobalJobStatisticsQueryResult;
import io.camunda.client.protocol.rest.StatusMetric;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class GlobalJobStatisticsTest extends ClientRestTest {

  @Test
  void shouldGetGlobalJobStatistics() {
    // given
    final GlobalJobStatisticsQueryResult response = createTestResponse();
    gatewayService.onGlobalJobStatisticsRequest(response);

    final OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime to = OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    // when
    final GlobalJobStatistics result = client.newGlobalJobStatisticsRequest(from, to).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).startsWith("/v2/jobs/statistics/global?");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);

    assertThat(result).isNotNull();
    assertThat(result.getCreated().getCount()).isEqualTo(100L);
    assertThat(result.getCompleted().getCount()).isEqualTo(80L);
    assertThat(result.getFailed().getCount()).isEqualTo(5L);
    assertThat(result.isIncomplete()).isFalse();
  }

  @Test
  void shouldGetGlobalJobStatisticsWithFilter() {
    // given
    final GlobalJobStatisticsQueryResult response = createTestResponse();
    gatewayService.onGlobalJobStatisticsRequest(response);

    final OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime to = OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    // when
    client.newGlobalJobStatisticsRequest(from, to).jobType("myJobType").send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl())
        .contains("from=" + urlEncode(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from)));
    assertThat(request.getUrl())
        .contains("to=" + urlEncode(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to)));
    assertThat(request.getUrl()).contains("jobType=myJobType");
  }

  @Test
  void shouldHandleIncompleteStatistics() {
    // given
    final GlobalJobStatisticsQueryResult response = new GlobalJobStatisticsQueryResult();
    response.setCreated(createStatusMetric(10L));
    response.setCompleted(createStatusMetric(5L));
    response.setFailed(createStatusMetric(1L));
    response.setIsIncomplete(true);
    gatewayService.onGlobalJobStatisticsRequest(response);

    final OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime to = OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    // when
    final GlobalJobStatistics result = client.newGlobalJobStatisticsRequest(from, to).send().join();

    // then
    assertThat(result.isIncomplete()).isTrue();
  }

  @Test
  void shouldHandleNullMetrics() {
    // given
    final GlobalJobStatisticsQueryResult response = new GlobalJobStatisticsQueryResult();
    response.setIsIncomplete(false);
    gatewayService.onGlobalJobStatisticsRequest(response);

    final OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime to = OffsetDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);

    // when
    final GlobalJobStatistics result = client.newGlobalJobStatisticsRequest(from, to).send().join();

    // then
    assertThat(result.getCreated().getCount()).isZero();
    assertThat(result.getCompleted().getCount()).isZero();
    assertThat(result.getFailed().getCount()).isZero();
    assertThat(result.getCreated().getLastUpdatedAt()).isNull();
    assertThat(result.getCompleted().getLastUpdatedAt()).isNull();
    assertThat(result.getFailed().getLastUpdatedAt()).isNull();
  }

  private GlobalJobStatisticsQueryResult createTestResponse() {
    final GlobalJobStatisticsQueryResult response = new GlobalJobStatisticsQueryResult();
    response.setCreated(createStatusMetric(100L));
    response.setCompleted(createStatusMetric(80L));
    response.setFailed(createStatusMetric(5L));
    response.setIsIncomplete(false);
    return response;
  }

  private StatusMetric createStatusMetric(final long count) {
    final StatusMetric metric = new StatusMetric();
    metric.setCount(count);
    metric.setLastUpdatedAt(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
    return metric;
  }

  private String urlEncode(final String value) {
    try {
      return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name());
    } catch (final java.io.UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
