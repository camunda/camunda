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

import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

abstract class AbstractSearchRequestImpl<S, T> extends TypedSearchRequestPropertyProvider<S>
    implements FinalSearchRequestStep<T>, FinalCommandStep<SearchResponse<T>> {

  ConsistencyPolicy<SearchResponse<T>> consistencyPolicy;
  final RequestConfig.Builder httpRequestConfig;

  AbstractSearchRequestImpl(final RequestConfig.Builder httpRequestConfig) {
    this.httpRequestConfig = httpRequestConfig;
  }

  @Override
  public FinalCommandStep<SearchResponse<T>> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public FinalCommandStep<SearchResponse<T>> consistencyPolicy(
      final Consumer<ConsistencyPolicy<SearchResponse<T>>> consistencyPolicyConsumer) {
    consistencyPolicy = new SearchRequestConsistencyPolicy<>();
    consistencyPolicyConsumer.accept(consistencyPolicy);
    return this;
  }

  @Override
  public FinalCommandStep<SearchResponse<T>> consistencyPolicy(
      final ConsistencyPolicy<SearchResponse<T>> consistencyPolicy) {
    this.consistencyPolicy = consistencyPolicy;
    return this;
  }

  @Override
  public FinalCommandStep<SearchResponse<T>> withDefaultConsistencyPolicy() {
    consistencyPolicy = new SearchRequestConsistencyPolicy<>();
    return this;
  }
}
