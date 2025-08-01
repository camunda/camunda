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
package io.camunda.client.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class UsageMetricsStatisticsTest extends ClientRestTest {

  private String formatDateTime(final OffsetDateTime dateTime) {
    try {
      return URLEncoder.encode(
          DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime), StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void shouldGetUsageMetricStatistics() {
    // when
    final OffsetDateTime startTime = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endTime = OffsetDateTime.now();
    client.newUsageMetricsRequest(startTime, endTime).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(
            "/v2/system/usage-metrics?startTime=%s&endTime=%s",
            formatDateTime(startTime), formatDateTime(endTime));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  void shouldGetUsageMetricStatisticsWithTenants() {
    // when
    final OffsetDateTime startTime = OffsetDateTime.now().minusDays(1);
    final OffsetDateTime endTime = OffsetDateTime.now();
    client
        .newUsageMetricsRequest(startTime, endTime)
        .withTenants(true)
        .tenantId("tenant1")
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(
            "/v2/system/usage-metrics?withTenants=true&tenantId=tenant1&startTime=%s&endTime=%s",
            formatDateTime(startTime), formatDateTime(endTime));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getBodyAsString()).isEmpty();
  }
}
