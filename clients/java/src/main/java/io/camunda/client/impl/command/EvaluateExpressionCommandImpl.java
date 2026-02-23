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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1;
import io.camunda.client.api.command.EvaluateExpressionCommandStep1.EvaluateExpressionCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.EvaluateExpressionResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.EvaluateExpressionResponseImpl;
import io.camunda.client.protocol.rest.ExpressionEvaluationRequest;
import io.camunda.client.protocol.rest.ExpressionEvaluationResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class EvaluateExpressionCommandImpl
    implements EvaluateExpressionCommandStep1, EvaluateExpressionCommandStep2 {

  private final ExpressionEvaluationRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public EvaluateExpressionCommandImpl(
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new ExpressionEvaluationRequest();
    tenantId(config.getDefaultTenantId());
  }

  @Override
  public EvaluateExpressionCommandStep2 expression(final String expression) {
    ArgumentUtil.ensureNotNull("expression", expression);
    request.setExpression(expression);
    return this;
  }

  @Override
  public EvaluateExpressionCommandStep2 tenantId(final String tenantId) {
    request.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<EvaluateExpressionResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<EvaluateExpressionResponse> send() {
    final HttpCamundaFuture<EvaluateExpressionResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/expression/evaluation",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ExpressionEvaluationResult.class,
        EvaluateExpressionResponseImpl::new,
        result);
    return result;
  }
}
