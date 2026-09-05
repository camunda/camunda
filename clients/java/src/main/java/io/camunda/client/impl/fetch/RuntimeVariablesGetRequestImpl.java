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
package io.camunda.client.impl.fetch;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.fetch.RuntimeVariablesGetRequest;
import io.camunda.client.api.response.RuntimeVariables;
import io.camunda.client.api.search.enums.RuntimeVariableScope;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.RuntimeVariablesImpl;
import io.camunda.client.protocol.rest.RuntimeVariablesResult;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class RuntimeVariablesGetRequestImpl implements RuntimeVariablesGetRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long scopeKey;
  private RuntimeVariableScope scope;

  public RuntimeVariablesGetRequestImpl(final HttpClient httpClient, final long scopeKey) {
    this.httpClient = httpClient;
    this.scopeKey = scopeKey;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public RuntimeVariablesGetRequest scope(final RuntimeVariableScope scope) {
    this.scope = scope;
    return this;
  }

  @Override
  public FinalCommandStep<RuntimeVariables> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<RuntimeVariables> send() {
    final HttpCamundaFuture<RuntimeVariables> result = new HttpCamundaFuture<>();
    final Map<String, String> queryParams = new HashMap<>();
    if (scope != null) {
      queryParams.put("scope", scope.name());
    }
    httpClient.get(
        String.format("/element-instances/%d/variables", scopeKey),
        queryParams,
        httpRequestConfig.build(),
        RuntimeVariablesResult.class,
        RuntimeVariablesImpl::new,
        result);
    return result;
  }
}
