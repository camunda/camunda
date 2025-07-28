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

import static io.camunda.client.api.search.request.SearchRequestBuilders.clientSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ClientFilter;
import io.camunda.client.api.search.request.ClientsByGroupSearchRequest;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.Client;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.GroupClientSearchQueryRequest;
import io.camunda.client.protocol.rest.GroupClientSearchResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ClientsByGroupSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<GroupClientSearchQueryRequest>
    implements ClientsByGroupSearchRequest {

  private final GroupClientSearchQueryRequest request;
  private final String groupId;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  public ClientsByGroupSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final String groupId) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.groupId = groupId;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new GroupClientSearchQueryRequest();
  }

  @Override
  public FinalSearchRequestStep<Client> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<Client>> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<SearchResponse<Client>> result = new HttpCamundaFuture<>();
    httpClient.post(
        String.format("/groups/%s/clients/search", groupId),
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        GroupClientSearchResult.class,
        SearchResponseMapper::toGroupClientsResponse,
        result);
    return result;
  }

  @Override
  public ClientsByGroupSearchRequest filter(final ClientFilter value) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public ClientsByGroupSearchRequest filter(final Consumer<ClientFilter> fn) {
    // This command doesn't support filtering
    throw new UnsupportedOperationException("This command does not support filtering");
  }

  @Override
  public ClientsByGroupSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ClientsByGroupSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public ClientsByGroupSearchRequest sort(final ClientSort value) {
    request.setSort(
        SearchRequestSortMapper.toGroupClientSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ClientsByGroupSearchRequest sort(final Consumer<ClientSort> fn) {
    return sort(clientSort(fn));
  }

  @Override
  protected GroupClientSearchQueryRequest getSearchRequestProperty() {
    return request;
  }
}
