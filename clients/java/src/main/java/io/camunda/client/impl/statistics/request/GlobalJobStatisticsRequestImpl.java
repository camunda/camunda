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
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.statistics.request.GlobalJobStatisticsRequest;
import io.camunda.client.api.statistics.response.GlobalJobStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.GlobalJobStatisticsQueryResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class GlobalJobStatisticsRequestImpl implements GlobalJobStatisticsRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final OffsetDateTime from;
  private final OffsetDateTime to;
  private String jobType;

  public GlobalJobStatisticsRequestImpl(
      final HttpClient httpClient, final OffsetDateTime from, final OffsetDateTime to) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.from = from;
    this.to = to;
  }

  @Override
  public FinalCommandStep<GlobalJobStatistics> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<GlobalJobStatistics> send() {
    final HttpCamundaFuture<GlobalJobStatistics> result = new HttpCamundaFuture<>();
    final Map<String, String> queryParams = new HashMap<>();

    queryParams.put("from", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from));
    queryParams.put("to", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to));
    if (jobType != null) {
      queryParams.put("jobType", jobType);
    }

    httpClient.get(
        "/jobs/statistics/global",
        queryParams,
        httpRequestConfig.build(),
        GlobalJobStatisticsQueryResult.class,
        StatisticsResponseMapper::toGlobalJobStatisticsResponse,
        result);
    return result;
  }

  @Override
  public GlobalJobStatisticsRequest jobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }
}
