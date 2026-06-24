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

import static io.camunda.client.api.search.request.SearchRequestBuilders.cursorForwardPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.page.CursorForwardPage;
import io.camunda.client.api.statistics.request.JobTimeSeriesStatisticsRequest;
import io.camunda.client.api.statistics.response.JobTimeSeriesStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.CursorForwardPagination;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsFilter;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsQuery;
import io.camunda.client.protocol.rest.JobTimeSeriesStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class JobTimeSeriesStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<JobTimeSeriesStatisticsQuery>
    implements JobTimeSeriesStatisticsRequest {

  private final JobTimeSeriesStatisticsQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public JobTimeSeriesStatisticsRequestImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final OffsetDateTime from,
      final OffsetDateTime to,
      final String jobType) {
    request = new JobTimeSeriesStatisticsQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    request.setFilter(
        new JobTimeSeriesStatisticsFilter()
            .from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from))
            .to(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to))
            .jobType(jobType));
  }

  @Override
  public JobTimeSeriesStatisticsRequest resolution(final Duration resolution) {
    request.getFilter().setResolution(resolution.toString());
    return this;
  }

  @Override
  public FinalCommandStep<JobTimeSeriesStatistics> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<JobTimeSeriesStatistics> send() {
    final HttpCamundaFuture<JobTimeSeriesStatistics> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/jobs/statistics/time-series",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        JobTimeSeriesStatisticsQueryResult.class,
        StatisticsResponseMapper::toJobTimeSeriesStatisticsResponse,
        result);
    return result;
  }

  @Override
  protected JobTimeSeriesStatisticsQuery getSearchRequestProperty() {
    return request;
  }

  @Override
  public JobTimeSeriesStatisticsRequest page(final CursorForwardPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(new CursorForwardPagination().limit(page.getLimit()).after(page.getAfter()));
    return this;
  }

  @Override
  public JobTimeSeriesStatisticsRequest page(
      final java.util.function.Consumer<CursorForwardPage> fn) {
    return page(cursorForwardPage(fn));
  }
}
