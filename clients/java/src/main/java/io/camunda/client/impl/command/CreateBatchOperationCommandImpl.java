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
package io.camunda.client.impl.command;

import static io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider.provideSearchRequestProperty;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep2;
import io.camunda.client.api.command.CreateBatchOperationCommandStep1.CreateBatchOperationCommandStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.SearchRequestBuilders;
import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateBatchOperationResponseImpl;
import io.camunda.client.protocol.rest.BatchOperationCreatedResult;
import io.camunda.client.protocol.rest.BatchOperationTypeEnum;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateBatchOperationCommandImpl<E extends SearchRequestFilter>
    implements CreateBatchOperationCommandStep2<E>, CreateBatchOperationCommandStep3<E> {
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;

  private final BatchOperationTypeEnum type;
  private final Supplier<E> filterFactory;
  private E filter;

  public CreateBatchOperationCommandImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final BatchOperationTypeEnum type,
      final Supplier<E> filterFactory) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();

    this.type = type;
    this.filterFactory = filterFactory;
  }

  @Override
  public CreateBatchOperationCommandStep3<E> filter(final E filter) {
    this.filter = Objects.requireNonNull(filter, "must specify a filter");
    return this;
  }

  @Override
  public CreateBatchOperationCommandStep3<E> filter(final Consumer<E> fn) {
    Objects.requireNonNull(fn, "must specify a filter consumer");
    filter = filterFactory.get();
    fn.accept(filter);
    return this;
  }

  @Override
  public FinalCommandStep<CreateBatchOperationResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateBatchOperationResponse> send() {
    final HttpCamundaFuture<CreateBatchOperationResponse> result = new HttpCamundaFuture<>();
    final CreateBatchOperationResponseImpl response = new CreateBatchOperationResponseImpl();
    httpClient.post(
        getUrl(),
        jsonMapper.toJson(provideSearchRequestProperty(filter)),
        httpRequestConfig.build(),
        BatchOperationCreatedResult.class,
        response::setResponse,
        result);
    return result;
  }

  private String getUrl() {
    switch (type) {
      case PROCESS_CANCELLATION:
        return "/process-instances/batch-operations/cancellation";
      default:
        throw new IllegalArgumentException("Unsupported batch operation type: " + type);
    }
  }

  public static class CreateBatchOperationCommandStep1Impl
      implements CreateBatchOperationCommandStep1 {

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;

    public CreateBatchOperationCommandStep1Impl(
        final HttpClient httpClient, final JsonMapper jsonMapper) {
      this.httpClient = httpClient;
      this.jsonMapper = jsonMapper;
    }

    @Override
    public CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceCancel() {
      return new CreateBatchOperationCommandImpl<>(
          httpClient,
          jsonMapper,
          BatchOperationTypeEnum.PROCESS_CANCELLATION,
          SearchRequestBuilders::processInstanceFilter);
    }
  }
}
