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
package io.camunda.zeebe.client.impl.search;

import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.decisionRequirementFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.decisionRequirementSort;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.searchRequestPage;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.userTaskFilter;
import static io.camunda.zeebe.client.api.search.SearchRequestBuilders.userTaskSort;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.search.DecisionRequirementFilter;
import io.camunda.zeebe.client.api.search.DecisionRequirementQuery;
import io.camunda.zeebe.client.api.search.DecisionRequirementSort;
import io.camunda.zeebe.client.api.search.FinalSearchQueryStep;
import io.camunda.zeebe.client.api.search.SearchRequestPage;
import io.camunda.zeebe.client.api.search.UserTaskFilter;
import io.camunda.zeebe.client.api.search.UserTaskQuery;
import io.camunda.zeebe.client.api.search.UserTaskSort;
import io.camunda.zeebe.client.api.search.response.DecisionRequirements;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementFilterRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementSearchQueryResponse;
import io.camunda.zeebe.client.protocol.rest.UserTaskFilterRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.UserTaskSearchQueryResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class DecisionRequirementQueryImpl
    extends TypedSearchRequestPropertyProvider<DecisionRequirementSearchQueryRequest>
    implements DecisionRequirementQuery {

  private final DecisionRequirementSearchQueryRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DecisionRequirementQueryImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new DecisionRequirementSearchQueryRequest();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchQueryStep<DecisionRequirements> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public HttpZeebeFuture<SearchQueryResponse<DecisionRequirements>> send() {
    final HttpZeebeFuture<SearchQueryResponse<DecisionRequirements>> result = new HttpZeebeFuture<>();
    httpClient.post(
        "/decision-requirements/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        DecisionRequirementSearchQueryResponse.class,
        SearchResponseMapper::toDecisionRequirementSearchResponse,
        result);
    return result;
  }

  @Override
  public DecisionRequirementQuery filter(final DecisionRequirementFilter value) {
    final DecisionRequirementFilterRequest filter = provideSearchRequestProperty(value);
    request.setFilter(filter);
    return this;
  }

  @Override
  public DecisionRequirementQuery filter(final Consumer<DecisionRequirementFilter> fn) {
    return filter(decisionRequirementFilter(fn));
  }

  @Override
  public DecisionRequirementQuery sort(final DecisionRequirementSort value) {
    final DecisionRequirementSortImpl sorting = (DecisionRequirementSortImpl) value;
    request.setSort(sorting.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementQuery sort(final Consumer<DecisionRequirementSort> fn) {
    return sort(decisionRequirementSort(fn));
  }

  @Override
  public DecisionRequirementQuery page(final SearchRequestPage value) {
    final SearchRequestPageImpl page = (SearchRequestPageImpl) value;
    request.setPage(page.getSearchRequestProperty());
    return this;
  }

  @Override
  public DecisionRequirementQuery page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected DecisionRequirementSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
