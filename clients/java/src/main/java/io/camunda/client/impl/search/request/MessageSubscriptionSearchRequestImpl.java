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

import static io.camunda.client.api.search.request.SearchRequestBuilders.messageSubscriptionFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.messageSubscriptionSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.MessageSubscriptionFilter;
import io.camunda.client.api.search.request.MessageSubscriptionSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.MessageSubscriptionSort;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.MessageSubscriptionSearchQuery;
import io.camunda.client.protocol.rest.MessageSubscriptionSearchQueryResult;
import java.util.function.Consumer;

public class MessageSubscriptionSearchRequestImpl
    extends AbstractSearchRequestImpl<MessageSubscriptionSearchQuery, MessageSubscription>
    implements MessageSubscriptionSearchRequest {

  private final MessageSubscriptionSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;

  public MessageSubscriptionSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    super(httpClient.newRequestConfig());
    request = new MessageSubscriptionSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
  }

  @Override
  public CamundaFuture<SearchResponse<MessageSubscription>> send() {

    return httpClient.post(
        "message-subscriptions/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        MessageSubscriptionSearchQueryResult.class,
        SearchResponseMapper::toMessageSubscriptionSearchResponse,
        consistencyPolicy);
  }

  @Override
  public MessageSubscriptionSearchRequest filter(final MessageSubscriptionFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MessageSubscriptionSearchRequest filter(final Consumer<MessageSubscriptionFilter> fn) {
    return filter(messageSubscriptionFilter(fn));
  }

  @Override
  public MessageSubscriptionSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public MessageSubscriptionSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  public MessageSubscriptionSearchRequest sort(final MessageSubscriptionSort value) {
    request.setSort(
        SearchRequestSortMapper.toMessageSubscriptionSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public MessageSubscriptionSearchRequest sort(final Consumer<MessageSubscriptionSort> fn) {
    return sort(messageSubscriptionSort(fn));
  }

  @Override
  protected MessageSubscriptionSearchQuery getSearchRequestProperty() {
    return request;
  }
}
