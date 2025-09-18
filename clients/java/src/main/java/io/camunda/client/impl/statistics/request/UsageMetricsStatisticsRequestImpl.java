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
package io.camunda.client.impl.statistics.request;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.statistics.request.UsageMetricsStatisticsRequest;
import io.camunda.client.api.statistics.response.UsageMetricsStatistics;
import io.camunda.client.impl.fetch.AbstractFetchRequestImpl;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.UsageMetricsResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class UsageMetricsStatisticsRequestImpl
    extends AbstractFetchRequestImpl<UsageMetricsStatistics>
    implements UsageMetricsStatisticsRequest {

  private final HttpClient httpClient;
  private final OffsetDateTime startTime;
  private final OffsetDateTime endTime;
  private String tenantId;
  private boolean withTenants = false;

  public UsageMetricsStatisticsRequestImpl(
      final HttpClient httpClient, final OffsetDateTime startTime, final OffsetDateTime endTime) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  @Override
  public CamundaFuture<UsageMetricsStatistics> send() {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put("startTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(startTime));
    queryParams.put("endTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(endTime));
    if (withTenants) {
      queryParams.put("withTenants", String.valueOf(true));
    }
    if (tenantId != null) {
      queryParams.put("tenantId", tenantId);
    }
    return httpClient.get(
        "/system/usage-metrics",
        queryParams,
        httpRequestConfig.build(),
        UsageMetricsResponse.class,
        StatisticsResponseMapper::toUsageMetricsResponse,
        consistencyPolicy);
  }

  @Override
  public UsageMetricsStatisticsRequest withTenants(final boolean withTenants) {
    this.withTenants = withTenants;
    return this;
  }

  @Override
  public UsageMetricsStatisticsRequest tenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }
}
