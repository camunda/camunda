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
package io.camunda.client.impl.search.request;

import static io.camunda.client.api.search.request.SearchRequestBuilders.jobFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.jobSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.JobSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.JobSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.JobSearchQuery;
import io.camunda.client.protocol.rest.JobSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class JobSearchRequestImpl extends TypedSearchRequestPropertyProvider<JobSearchQuery>
    implements JobSearchRequest {

  private final JobSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public JobSearchRequestImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new JobSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<Job> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpCamundaFuture<SearchResponse<Job>> send() {
    final HttpCamundaFuture<SearchResponse<Job>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "jobs/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        JobSearchQueryResult.class,
        SearchResponseMapper::toJobSearchResponse,
        result);
    return result;
  }

  @Override
  public JobSearchRequest filter(final JobFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public JobSearchRequest filter(final Consumer<JobFilter> fn) {
    return filter(jobFilter(fn));
  }

  @Override
  public JobSearchRequest sort(final JobSort value) {
    request.setSort(
        SearchRequestSortMapper.toJobSearchQuerySortRequest(provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public JobSearchRequest sort(final Consumer<JobSort> fn) {
    return sort(jobSort(fn));
  }

  @Override
  public JobSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public JobSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected JobSearchQuery getSearchRequestProperty() {
    return request;
  }
}
