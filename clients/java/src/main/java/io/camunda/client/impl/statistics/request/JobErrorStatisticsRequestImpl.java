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
import static io.camunda.client.api.search.request.SearchRequestBuilders.jobErrorStatisticsFilter;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.page.CursorForwardPage;
import io.camunda.client.api.statistics.filter.JobErrorStatisticsFilter;
import io.camunda.client.api.statistics.request.JobErrorStatisticsRequest;
import io.camunda.client.api.statistics.response.JobErrorStatistics;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.statistics.response.StatisticsResponseMapper;
import io.camunda.client.protocol.rest.CursorForwardPagination;
import io.camunda.client.protocol.rest.JobErrorStatisticsQuery;
import io.camunda.client.protocol.rest.JobErrorStatisticsQueryResult;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class JobErrorStatisticsRequestImpl
    extends TypedSearchRequestPropertyProvider<JobErrorStatisticsQuery>
    implements JobErrorStatisticsRequest {

  private final JobErrorStatisticsQuery request;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public JobErrorStatisticsRequestImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final OffsetDateTime from,
      final OffsetDateTime to,
      final String jobType) {
    request = new JobErrorStatisticsQuery();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    request.setFilter(
        new io.camunda.client.protocol.rest.JobErrorStatisticsFilter()
            .from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from))
            .to(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to))
            .jobType(jobType));
  }

  @Override
  public FinalCommandStep<JobErrorStatistics> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<JobErrorStatistics> send() {
    final HttpCamundaFuture<JobErrorStatistics> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/jobs/statistics/errors",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        JobErrorStatisticsQueryResult.class,
        StatisticsResponseMapper::toJobErrorStatisticsResponse,
        result);
    return result;
  }

  @Override
  protected JobErrorStatisticsQuery getSearchRequestProperty() {
    return request;
  }

  @Override
  public JobErrorStatisticsRequest filter(final JobErrorStatisticsFilter value) {
    final io.camunda.client.protocol.rest.JobErrorStatisticsFilter filterProperty =
        provideSearchRequestProperty(value);
    // Preserve the from/to/jobType that were set in the constructor
    if (request.getFilter() != null) {
      final String fromValue = request.getFilter().getFrom();
      final String toValue = request.getFilter().getTo();
      final String jobTypeValue = request.getFilter().getJobType();
      filterProperty.from(fromValue).to(toValue).jobType(jobTypeValue);
    }
    request.setFilter(filterProperty);
    return this;
  }

  @Override
  public JobErrorStatisticsRequest filter(final Consumer<JobErrorStatisticsFilter> fn) {
    return filter(jobErrorStatisticsFilter(fn));
  }

  @Override
  public JobErrorStatisticsRequest page(final CursorForwardPage value) {
    final SearchQueryPageRequest page = provideSearchRequestProperty(value);
    request.setPage(new CursorForwardPagination().limit(page.getLimit()).after(page.getAfter()));
    return this;
  }

  @Override
  public JobErrorStatisticsRequest page(final Consumer<CursorForwardPage> fn) {
    return page(cursorForwardPage(fn));
  }
}
