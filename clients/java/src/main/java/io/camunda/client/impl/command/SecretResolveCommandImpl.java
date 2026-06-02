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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.SecretResolveCommandStep1;
import io.camunda.client.api.response.SecretResolveResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.SecretResolveResponseImpl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class SecretResolveCommandImpl implements SecretResolveCommandStep1 {

  private static final String PATH = "/secrets/resolve";

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final List<String> references = new ArrayList<>();

  public SecretResolveCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final Duration requestTimeout) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    requestTimeout(requestTimeout);
  }

  @Override
  public SecretResolveCommandStep1 references(final String... references) {
    if (references != null) {
      this.references.addAll(Arrays.asList(references));
    }
    return this;
  }

  @Override
  public SecretResolveCommandStep1 references(final List<String> references) {
    if (references != null) {
      this.references.addAll(references);
    }
    return this;
  }

  @Override
  public FinalCommandStep<SecretResolveResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SecretResolveResponse> send() {
    final HttpCamundaFuture<SecretResolveResponse> result = new HttpCamundaFuture<>();
    final Request request = new Request(references);
    httpClient.post(
        PATH,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        SecretResolveResponseImpl.class,
        response -> response,
        result);
    return result;
  }

  /** Wire-format request body. Static-nested to keep it private to the command. */
  static final class Request {
    @JsonProperty("references")
    private final List<String> references;

    Request(final List<String> references) {
      this.references = references;
    }

    public List<String> getReferences() {
      return references;
    }
  }
}
